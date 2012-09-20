/*
 * EpsGraphics2D.java
 *
 * Copyright (C) 2006 Nordic Growth Market NGM AB.
 */

package se.ngm.ditaaeps;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.io.PrintWriter;
import java.text.AttributedCharacterIterator;
import java.util.Map;

/** A Graphics2D that paints to an EPS PrintWriter instead of to a screen
 * or an image.
 * <p>
 * Only the methods necessary for EpsRenderer are implemented, i.e. the following:
 * <ul>
 * <li>Fill and draw shapes
 * <li>Draw string at x-y
 * <li>Color, Font (only size) and stroke (only BasicStroke)
 * </ul>
 * <p>
 * The <code>dispose</code> method must be called to "close" the EPS.
 * 
 * @author Mikael Brannstrom
 */
public class EpsGraphics2D extends Graphics2D {
    
    private PrintWriter out;
    private AffineTransform transform = new AffineTransform();

    private Stroke stroke = new BasicStroke();
    private boolean isStrokeDirty = true;
    private Color color = Color.BLACK;
    private boolean isColorDirty = true;
    private Font font = null;
    private boolean isFontDirty = true;
    
    /** Creates a new instance of EpsGraphics2D.
     * @param out where the EPS will be written to.
     * @param boundingBox the bounding box of the EPS.
     */
    public EpsGraphics2D(PrintWriter out, Rectangle2D boundingBox) {
        this.out = out;
        initEps(boundingBox);
    }
    
    private void initEps(Rectangle2D bounds) {
        out.println("%!PS-Adobe-3.0 EPSF-3.0");
        out.println("%%BoundingBox: "+
                (int)bounds.getMinX()+" "+
                (int)bounds.getMinY()+" "+
                (int)bounds.getMaxX()+" "+
                (int)bounds.getMaxY());
        out.println("%%HiResBoundingBox: "+
                bounds.getMinX()+" "+
                bounds.getMinY()+" "+
                bounds.getMaxX()+" "+
                bounds.getMaxY());
        out.println("%%Creator: DitaaEps");
        out.println("%%EndComments");
        out.println("%%BeginProlog");
        out.println("%%EndProlog");
    }

    private void printPath(Shape s) {
        PathIterator it = s.getPathIterator(transform);
        double[] pt = new double[6];
        boolean isClosed = true;
        double prevX=0, prevY=0;
        out.println("newpath");
        while(!it.isDone()) {
            isClosed = false;
            switch(it.currentSegment(pt)) {
                case PathIterator.SEG_CLOSE:
                    out.println("closepath");
                    isClosed = true;
                    break;
                case PathIterator.SEG_MOVETO:
                    out.println(""+pt[0]+" "+pt[1]+" moveto");
                    break;
                case PathIterator.SEG_LINETO:
                    out.println(""+pt[0]+" "+pt[1]+" lineto");
                    break;
                case PathIterator.SEG_QUADTO:
                    // convert to cubic
                    pt[4] = pt[2]; pt[5] = pt[3];
                    pt[0] = (prevX+2.0*pt[0])/3.0;
                    pt[1] = (prevY+2.0*pt[1])/3.0;
                    pt[2] = (pt[4]-prevX)/3.0 + pt[0];
                    pt[3] = (pt[5]-prevY)/3.0 + pt[1];
                case PathIterator.SEG_CUBICTO:
                    out.println(""+pt[0]+" "+pt[1]+" "+pt[2]+" "+pt[3]+" "+pt[4]+" "+pt[5]+" curveto");
                    break;
            }
            prevX = pt[0]; prevY = pt[1];
            it.next();
        }
    }
    
    private void printColor() {
        if(isColorDirty) {
            out.println(""+
                    ((double)color.getRed()/255.0)+" "+
                    ((double)color.getGreen()/255.0)+" "+
                    ((double)color.getBlue()/255.0)+" setrgbcolor");
            isColorDirty = false;
        }
    }
    
    private void printStroke() {
        if(isStrokeDirty) {
            if(stroke instanceof BasicStroke) {
                BasicStroke bs = (BasicStroke)stroke;
                out.println(""+bs.getLineWidth()+" setlinewidth");
                switch(bs.getEndCap()) {
                    case BasicStroke.CAP_BUTT:
                        out.print("0");
                        break;
                    case BasicStroke.CAP_ROUND:
                        out.print("1");
                        break;
                    case BasicStroke.CAP_SQUARE:
                        out.print("2");
                        break;
                }
                out.println(" setlinecap");
                float[] dash = bs.getDashArray();
                if(dash != null) {
                    out.print("[");
                    for(int i=0; i<dash.length; i++) {
                        if(i != 0) 
                            out.print(" ");
                        out.print(dash[i]);
                    }
                    out.println("] "+bs.getDashPhase()+" setdash");
                } else {
                    out.println("[] 0 setdash");
                }
            }
            isStrokeDirty = false;
        }
    }
    
    private void printFont() {
        if(isFontDirty) {
            out.println("/Times-Roman findfont");
            out.println((font.getSize() * 4 / 3) + " scalefont setfont");
            isFontDirty = false;
        }
    }
    
    public void draw(Shape s) {
        printColor();
        printStroke();
        printPath(s);
        out.println("stroke");
    }

    public void fill(Shape s) {
        printColor();
        printPath(s);
        out.println("fill");
    }

    public void drawString(String str, int x, int y) {
        drawString(str, (float)x, (float)y);
    }

    public void drawString(String s, float x, float y) {
        printFont();
        float[] pt = new float[]{x, y};
        transform.transform(pt, 0, pt, 0, 1);
        out.println(""+pt[0]+" "+pt[1]+" moveto");
        out.println("("+escape(s)+") show");
    }
    
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length()+8);
        for(int i=0; i<s.length(); i++) {
            char ch = s.charAt(i);
            switch(ch) {
            case '(':
                sb.append("\\(");
                break;
            case ')':
                sb.append("\\)");
                break;
            case '\\':
                sb.append("\\\\");
                break;
            default:
                if(ch > 128) {
                    sb.append('\\');
                    for(int j=3; j>0; j--) {
                        sb.append((char)((ch >> 8*j) & 0x7 + '0'));
                    }
                } else {
                    sb.append(ch);
                }
            }
        }
        return sb.toString();
    }

    public void setStroke(Stroke s) {
        isStrokeDirty = isStrokeDirty || (this.stroke != s);
        this.stroke = s;
    }

    public void translate(int x, int y) {
        transform.translate(x, y);
    }

    public void translate(double tx, double ty) {
        transform.translate(tx, ty);
    }

    public void rotate(double theta) {
        transform.rotate(theta);
    }

    public void rotate(double theta, double x, double y) {
        transform.rotate(theta, x, y);
    }

    public void scale(double sx, double sy) {
        transform.scale(sx, sy);
    }

    public void shear(double shx, double shy) {
        transform.shear(shx, shy);
    }

    public void transform(AffineTransform Tx) {
        transform.concatenate(Tx);
    }

    public void setTransform(AffineTransform Tx) {
        transform.setTransform(Tx);
    }

    public AffineTransform getTransform() {
        return transform;
    }

    public Stroke getStroke() {
        return stroke;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color c) {
        isColorDirty = isColorDirty || (this.color != c);
        this.color = c;
    }

    public Font getFont() {
        return font;
    }

    public void setFont(Font font) {
        this.font = font;
    }
   
    public void dispose() {
        out.println("showpage");
        out.println("%%Trailer");
        out.println("%%EOF");
        out.flush();
        out.close();
    }
    
    
    // -------------------------------------------------------------------------
    // NOT IMPLEMENTED METHODS
    // -------------------------------------------------------------------------
    
    public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public Object getRenderingHint(RenderingHints.Key hintKey) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void setRenderingHints(Map/*<?, ?>*/ hints) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void addRenderingHints(Map/*<?, ?>*/ hints) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public RenderingHints getRenderingHints() {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void drawString(AttributedCharacterIterator iterator, int x, int y) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void drawString(AttributedCharacterIterator iterator, float x, float y) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void drawGlyphVector(GlyphVector g, float x, float y) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public Paint getPaint() {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public Composite getComposite() {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void clip(Shape s) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public FontRenderContext getFontRenderContext() {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public Graphics create() {
        throw new RuntimeException("Not implemented"); // FIXME
    }
    public void setBackground(Color color) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public Color getBackground() {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public GraphicsConfiguration getDeviceConfiguration() {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void setComposite(Composite comp) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void setPaint(Paint paint) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void setPaintMode() {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void setXORMode(Color c1) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public FontMetrics getFontMetrics(Font f) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public Rectangle getClipBounds() {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void clipRect(int x, int y, int width, int height) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void setClip(int x, int y, int width, int height) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public Shape getClip() {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void setClip(Shape clip) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void copyArea(int x, int y, int width, int height, int dx, int dy) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void drawLine(int x1, int y1, int x2, int y2) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void fillRect(int x, int y, int width, int height) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void clearRect(int x, int y, int width, int height) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void drawOval(int x, int y, int width, int height) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void fillOval(int x, int y, int width, int height) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor, ImageObserver observer) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, ImageObserver observer) {
        throw new RuntimeException("Not implemented"); // FIXME
    }

    public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2, Color bgcolor, ImageObserver observer) {
        throw new RuntimeException("Not implemented"); // FIXME
    }
}
