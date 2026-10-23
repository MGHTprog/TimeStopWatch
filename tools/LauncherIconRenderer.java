import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class LauncherIconRenderer {
    static Color navy = Color.decode("#101827"), white=Color.decode("#F5FAFF");
    static void artwork(Graphics2D g, boolean mono) {
        g.setColor(mono ? Color.decode("#244B43") : white);
        g.fill(new RoundRectangle2D.Double(48,22,12,5,2,2));
        g.fill(new Rectangle2D.Double(51.5,26,5,7));
        int[] starts={150,270,30}; String[] colors={"#36D996","#F5C84C","#FF6478"};
        g.setStroke(new BasicStroke(4.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        for(int i=0;i<3;i++) {
            g.setColor(mono ? Color.decode("#244B43") : Color.decode(colors[i]));
            g.draw(new Arc2D.Double(29.5,32.5,49,49,-starts[i],-104,Arc2D.OPEN));
        }
        g.setColor(mono ? Color.decode("#244B43") : white);
        g.setStroke(new BasicStroke(3.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        Path2D hands=new Path2D.Double(); hands.moveTo(54,43);hands.lineTo(54,57);hands.lineTo(65,63);g.draw(hands);
        g.fill(new Ellipse2D.Double(51.25,54.25,5.5,5.5));
    }
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]);
        String[] densities={"mdpi","hdpi","xhdpi","xxhdpi","xxxhdpi"};
        int[] sizes={48,72,96,144,192};
        for(int index=0;index<densities.length;index++) {
            File directory=new File(root,"app/src/main/res/mipmap-"+densities[index]);
            directory.mkdirs();
            for(boolean round:new boolean[]{false,true}) {
                BufferedImage icon=new BufferedImage(sizes[index],sizes[index],BufferedImage.TYPE_INT_ARGB);
                Graphics2D g=icon.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.scale(sizes[index]/108.0,sizes[index]/108.0);
                g.setColor(navy);
                if(round)g.fill(new Ellipse2D.Double(4,4,100,100));
                else g.fill(new RoundRectangle2D.Double(4,4,100,100,48,48));
                g.translate(54,54);g.scale(1.35,1.35);g.translate(-54,-54);artwork(g,false);
                g.dispose();
                ImageIO.write(icon,"png",new File(directory,round?"ic_launcher_round.png":"ic_launcher.png"));
            }
        }
        BufferedImage image=new BufferedImage(1000,330,BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas=image.createGraphics();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setColor(Color.decode("#E8EDF3"));canvas.fillRect(0,0,1000,330);
        String[] labels={"ROUND", "SQUIRCLE", "LEGACY", "THEMED"};
        for(int i=0;i<4;i++) {
            Graphics2D g=(Graphics2D)canvas.create();g.translate(45+i*250,35);g.scale(1.55,1.55);
            if(i==2) {
                g.setColor(navy);g.fill(new RoundRectangle2D.Double(4,4,100,100,48,48));
                g.translate(54,54);g.scale(1.35,1.35);g.translate(-54,-54);artwork(g,false);
            } else {
                Shape mask=i==1?new RoundRectangle2D.Double(0,0,108,108,45,45):new Ellipse2D.Double(0,0,108,108);
                g.clip(mask);g.setColor(i==3?Color.decode("#C4E6D8"):navy);g.fillRect(0,0,108,108);
                g.translate(-27,-27);g.scale(1.5,1.5);artwork(g,i==3);
            }
            g.dispose();
            canvas.setColor(navy);canvas.setFont(new Font("SansSerif",Font.BOLD,17));
            canvas.drawString(labels[i],55+i*250,252);
        }
        canvas.dispose();ImageIO.write(image,"png",new File(root,"validation/09-icon-preview.png"));
    }
}
