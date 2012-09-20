/*
 * EPS extension to DiTAA (Diagrams Through Ascii Art)
 *
 * Copyright (C) 2006 Nordic Growth Market NGM AB,
 * Mikael Brannstrom.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 */
package se.ngm.ditaaeps;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.cli.ParseException;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import org.stathissideris.ascii2image.core.FileUtils;
import org.stathissideris.ascii2image.graphics.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;


import org.stathissideris.ascii2image.core.ConversionOptions;
import org.stathissideris.ascii2image.core.RenderingOptions;
import org.stathissideris.ascii2image.core.Shape3DOrderingComparator;
import org.stathissideris.ascii2image.text.StringUtils;
import org.stathissideris.ascii2image.text.TextGrid;
import se.ngm.ditaaeps.EpsGraphics2D;

/** Modification of the BitmapRenderer to render EPS instead of PNG.
 *
 * @author Efstathios Sideris
 * @author Mikael Brannstrom
 */
public class EpsRenderer {

  private static final boolean DEBUG = false;

  private static void printUsage(){
    System.out.println("Usage:");
    System.out.println("\tjava -jar text2image.jar\n");
    System.out.println("\t[{-o,--overwrite}]");
    System.out.println("\t[{-d,--debug}]");
    System.out.println("\t[{-t,--tabs}]");

    System.out.println("\n\t[{-S,--no-shadows}]");
    System.out.println("\t[{-A,--no-antialias}]");
    System.out.println("\t[{-s,--scale} scale]");
    System.out.println("\t[{-r,--round-corners}]");
    System.out.println("\t[{-E,--no-separation}]");
    System.out.println("\n\t<inputfile> [outfile]");
  }

  private static String[] markupModeAllowedValues = {"use", "ignore", "render"};

  public static void main(String[] args){
    System.setProperty("java.awt.headless", "true");
    long startTime = System.currentTimeMillis();

    Options cmdOptions = new Options();
    cmdOptions.addOption(
        OptionBuilder.withLongOpt("help")
        .withDescription( "Prints usage help." )
        .create() );
    cmdOptions.addOption("v", "verbose", false, "Makes ditaa more verbose.");
    cmdOptions.addOption("o", "overwrite", false, "If the filename of the destination image already exists, an alternative name is chosen. If the overwrite option is selected, the image file is instead overwriten.");
    cmdOptions.addOption("S", "no-shadows", false, "Turns off the drop-shadow effect.");
    cmdOptions.addOption("A", "no-antialias", false, "Turns anti-aliasing off.");
    cmdOptions.addOption("W", "fixed-slope", false, "Makes sides of parallelograms and trapezoids fixed slope instead of fixed width.");
    cmdOptions.addOption("d", "debug", false, "Renders the debug grid over the resulting image.");
    cmdOptions.addOption("r", "round-corners", false, "Causes all corners to be rendered as round corners.");
    cmdOptions.addOption("E", "no-separation", false, "Prevents the separation of common edges of shapes.");
    cmdOptions.addOption("h", "html", false, "In this case the input is an HTML file. The contents of the <pre class=\"textdiagram\"> tags are rendered as diagrams and saved in the images directory and a new HTML file is produced with the appropriate <img> tags.");
    cmdOptions.addOption("T", "transparent", false, "Causes the diagram to be rendered on a transparent background. Overrides --background.");

    cmdOptions.addOption(
        OptionBuilder.withLongOpt("encoding")
        .withDescription("The encoding of the input file.")
        .hasArg()
        .withArgName("ENCODING")
        .create('e')
        );

    cmdOptions.addOption(
        OptionBuilder.withLongOpt("scale")
        .withDescription("A natural number that determines the size of the rendered image. The units are fractions of the default size (2.5 renders 1.5 times bigger than the default).")
        .hasArg()
        .withArgName("SCALE")
        .create('s')
        );

    cmdOptions.addOption(
        OptionBuilder.withLongOpt("tabs")
        .withDescription("Tabs are normally interpreted as 8 spaces but it is possible to change that using this option. It is not advisable to use tabs in your diagrams.")
        .hasArg()
        .withArgName("TABS")
        .create('t')
        );

    cmdOptions.addOption(
        OptionBuilder.withLongOpt("background")
        .withDescription("The background colour of the image. The format should be a six-digit hexadecimal number (as in HTML, FF0000 for red). Pass an eight-digit hex to define transparency. This is overridden by --transparent.")
        .hasArg()
        .withArgName("BACKGROUND")
        .create('b')
        );

    CommandLine cmdLine = null;

    try {
      CommandLineParser parser = new PosixParser();
      cmdLine = parser.parse(cmdOptions, args);

    } catch (org.apache.commons.cli.ParseException e) {
      System.err.println(e.getMessage());
      printUsage();
      System.exit(2);
    }

    if((cmdLine.hasOption("help")) || args.length == 0 ){
      printUsage();
      System.exit(0);
    }

    ConversionOptions options = null;

    try {
      options = new ConversionOptions(cmdLine);
    } catch(Exception ignore) {
      printUsage();
      System.exit(2);
    }

    if (options == null) {
      printUsage();
      System.exit(2);
    }

    args = cmdLine.getArgs();

    if(args.length == 0) {
      System.err.println("Error: Please provide the input file filename");
      printUsage();
      System.exit(2);
    }

    TextGrid grid = new TextGrid();
    String filename = args[0];
    System.out.println("Reading file: "+filename);
    try {
      if(!grid.loadFrom(filename, options.processingOptions)){
        System.err.println("Cannot open file "+filename+" for reading");
      }
    } catch (FileNotFoundException e1) {
      System.err.println("Error: File "+filename+" does not exist");
      System.exit(1);
    } catch (IOException e1) {
      System.err.println("Error: Cannot open file "+filename+" for reading");
      System.exit(1);
    }

    if(options.processingOptions.printDebugOutput()){
      System.out.println("Using grid:");
      grid.printDebug();
    }

    boolean overwrite = false;
    if(options.processingOptions.overwriteFiles()) overwrite = true;
    String toFilename;
    if(args.length == 1){
      toFilename = FileUtils.makeTargetPathname(filename, "eps", overwrite);
    } else {
      toFilename = args[1];
    }

    Diagram diagram = new Diagram(grid, options);
    System.out.println("Rendering to file: "+toFilename);


    try {
      PrintWriter out = new PrintWriter(new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(toFilename))));
      EpsRenderer.renderToEps(diagram, out, options.renderingOptions);
      out.close();
    } catch (IOException e) {
      //e.printStackTrace();
      System.err.println("Error: Cannot write to file "+filename);
      System.exit(1);
    }

    //BitmapRenderer.renderToPNG(diagram, toFilename, options.renderingOptions);

    long endTime = System.currentTimeMillis();
    double totalTime  = (endTime - startTime) / 1000.0;
    System.out.println("Done in "+totalTime+" sec");
  }


  public static void renderToEps(Diagram diagram, PrintWriter out, RenderingOptions options) {
    //RenderedImage renderedImage = image;
    EpsGraphics2D g2 = new EpsGraphics2D(out, new Rectangle2D.Double(0, -diagram.getHeight(), diagram.getWidth(), diagram.getHeight()));

    g2.scale(1, -1); // g2 origo is top-left, eps is bottom-left

    Object antialiasSetting = antialiasSetting = RenderingHints.VALUE_ANTIALIAS_OFF;
    if(options.performAntialias())
      antialiasSetting = RenderingHints.VALUE_ANTIALIAS_ON;

    //g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasSetting);

    g2.setColor(Color.white);
    //TODO: find out why the next line does not work
    //g2.fillRect(0, 0, image.getWidth()+10, image.getHeight()+10);
    /*for(int y = 0; y < diagram.getHeight(); y ++)
      g2.drawLine(0, y, diagram.getWidth(), y);*/

    g2.setStroke(new BasicStroke(1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND));

    ArrayList shapes = diagram.getAllDiagramShapes();

    if(DEBUG) System.out.println("Rendering "+shapes.size()+" shapes (groups flattened)");

    Iterator shapesIt;
    if(options.dropShadows()){
      //render shadows
      shapesIt = shapes.iterator();
      while(shapesIt.hasNext()){
        DiagramShape shape = (DiagramShape) shapesIt.next();

        if(shape.getPoints().isEmpty()) continue;

        //GeneralPath path = shape.makeIntoPath();
        GeneralPath path;
        path = shape.makeIntoRenderPath(diagram);

        float offset = diagram.getMinimumOfCellDimension() / 3.333f;

        if(path != null && shape.dropsShadow()){
          GeneralPath shadow = new GeneralPath(path);
          AffineTransform translate = new AffineTransform();
          translate.setToTranslation(offset, offset);
          shadow.transform(translate);
          g2.setColor(new Color(150,150,150));
          g2.fill(shadow);

        }
      }


      //blur shadows

      //            if(true) {
      //                int blurRadius = 6;
      //                int blurRadius2 = blurRadius * blurRadius;
      //                float blurRadius2F = blurRadius2;
      //                float weight = 1.0f / blurRadius2F;
      //                float[] elements = new float[blurRadius2];
      //                for (int k = 0; k < blurRadius2; k++)
      //                    elements[k] = weight;
      //                Kernel myKernel = new Kernel(blurRadius, blurRadius, elements);
      //
      //                //if EDGE_NO_OP is not selected, EDGE_ZERO_FILL is the default which creates a black border
      //                ConvolveOp simpleBlur =
      //                        new ConvolveOp(myKernel, ConvolveOp.EDGE_NO_OP, null);
      //                //BufferedImage destination = new BufferedImage(image.getWidth()+blurRadius, image.getHeight()+blurRadius, image.getType());
      //                BufferedImage destination =
      //                        new BufferedImage(
      //                        image.getWidth(),
      //                        image.getHeight(),
      //                        image.getType());
      //                simpleBlur.filter(image, destination);
      //                //destination = destination.getSubimage(blurRadius/2, blurRadius/2, image.getWidth(), image.getHeight());
      //                g2 = destination.createGraphics();
      //                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasSetting);
      //                renderedImage = destination;
      //            }
    }


    //fill and stroke

    float dashInterval = Math.min(diagram.getCellWidth(), diagram.getCellHeight()) / 2;
    //Stroke normalStroke = g2.getStroke();

    float strokeWeight = diagram.getMinimumOfCellDimension() / 10;

    Stroke normalStroke =
      new BasicStroke(
          strokeWeight,
          //10,
          BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND
          );

    Stroke dashStroke =
      new BasicStroke(
          strokeWeight,
          BasicStroke.CAP_BUTT,
          BasicStroke.JOIN_ROUND,
          0,
          new float[] {dashInterval},
          0
          );


    //find storage shapes
    ArrayList storageShapes = new ArrayList();
    shapesIt = shapes.iterator();
    while(shapesIt.hasNext()){
      DiagramShape shape = (DiagramShape) shapesIt.next();
      if(shape.getType() == DiagramShape.TYPE_STORAGE) {
        storageShapes.add(shape);
        continue;
      }
    }



    //render storage shapes
    //special case since they are '3d' and should be
    //rendered bottom to top
    //TODO: known bug: if a storage object is within a bigger normal box, it will be overwritten in the main drawing loop
    //(BUT this is not possible since tags are applied to all shapes overlaping shapes)


    Collections.sort(storageShapes, new Shape3DOrderingComparator());

    g2.setStroke(normalStroke);
    shapesIt = storageShapes.iterator();
    while(shapesIt.hasNext()){
      DiagramShape shape = (DiagramShape) shapesIt.next();

      GeneralPath path;
      path = shape.makeIntoRenderPath(diagram);

      if(!shape.isStrokeDashed()) {
        if(shape.getFillColor() != null)
          g2.setColor(shape.getFillColor());
        else
          g2.setColor(Color.white);
        g2.fill(path);
      }

      if(shape.isStrokeDashed())
        g2.setStroke(dashStroke);
      else
        g2.setStroke(normalStroke);
      g2.setColor(shape.getStrokeColor());
      g2.draw(path);
    }


    //render the rest of the shapes
    ArrayList pointMarkers = new ArrayList();
    shapesIt = shapes.iterator();
    while(shapesIt.hasNext()){
      DiagramShape shape = (DiagramShape) shapesIt.next();
      if(shape.getType() == DiagramShape.TYPE_POINT_MARKER) {
        pointMarkers.add(shape);
        continue;
      }
      if(shape.getType() == DiagramShape.TYPE_STORAGE) {
        continue;
      }


      if(shape.getPoints().isEmpty()) continue;

      int size = shape.getPoints().size();

      GeneralPath path;
      path = shape.makeIntoRenderPath(diagram);

      if(path != null && shape.isClosed() && !shape.isStrokeDashed()){
        if(shape.getFillColor() != null)
          g2.setColor(shape.getFillColor());
        else
          g2.setColor(Color.white);
        g2.fill(path);
      }
      if(shape.getType() != DiagramShape.TYPE_ARROWHEAD){
        g2.setColor(shape.getStrokeColor());
        if(shape.isStrokeDashed())
          g2.setStroke(dashStroke);
        else
          g2.setStroke(normalStroke);
        g2.draw(path);
      }
    }

    //render point markers

    g2.setStroke(normalStroke);
    shapesIt = pointMarkers.iterator();
    while(shapesIt.hasNext()){
      DiagramShape shape = (DiagramShape) shapesIt.next();
      //if(shape.getType() != DiagramShape.TYPE_POINT_MARKER) continue;

      GeneralPath path;
      path = shape.makeIntoRenderPath(diagram);

      g2.setColor(Color.white);
      g2.fill(path);
      g2.setColor(shape.getStrokeColor());
      g2.draw(path);
    }


    //handle text
    //g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

    Iterator textIt = diagram.getTextObjects().iterator();
    while(textIt.hasNext()){
      DiagramText text = (DiagramText) textIt.next();
      g2.setColor(text.getColor());
      g2.setFont(text.getFont());
      g2.drawString(text.getText(), text.getXPos(), text.getYPos());
    }


    if(options.renderDebugLines() || DEBUG){
      Stroke debugStroke =
        new BasicStroke(
            1,
            BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND
            );
      g2.setStroke(debugStroke);
      g2.setColor(new Color(170, 170, 170));
      g2.setXORMode(Color.white);
      for(int x = 0; x < diagram.getWidth(); x += diagram.getCellWidth())
        g2.drawLine(x, 0, x, diagram.getHeight());
      for(int y = 0; y < diagram.getHeight(); y += diagram.getCellHeight())
        g2.drawLine(0, y, diagram.getWidth(), y);
    }


    g2.dispose();
  }

  public static boolean isColorDark(Color color){
    int brightness = Math.max(color.getRed(), color.getGreen());
    brightness = Math.max(color.getBlue(), brightness);
    if(brightness < 200) {
      if(DEBUG) System.out.println("Color "+color+" is dark");
      return true;
    }
    if(DEBUG) System.out.println("Color "+color+" is not dark");
    return false;
  }
}
