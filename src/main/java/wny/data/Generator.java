package wny.data;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

/** 
 * Synthetic generator
 * This class allows us to generate synthetic data with different distributions
 * In the uniform dataset, values for each ranking attribute are generated uniformly at random, and independent of the other attributes. 
 * In the correlated dataset, a tuple with a high (low) value in one ranking attribute is likely to also have high (low) values for the others. 
 * In the anti-correlated dataset, a tuple with a high (low) value in one ranking attribute is likely to 
 * also have high (low) values for half of the other attributes, but more likely to receive low (high) values for the other half.
 * @author Zixuan Chen
*/
public class Generator {
    public int num_tuple;
    public int num_attribute;
    public String filename;

    public Generator (int num_tuple, int num_attribute, String filename) {
		this.num_tuple = num_tuple;
        this.num_attribute = num_attribute;
        this.filename = filename;
	}

    /** 
     * Get a correlated value based on the input value
     * @param a The input value
     * @return A correlated value to the input value
    */
    private double getCorrelated(double a) {
        Random r = new Random();
        double v = (r.nextDouble() - 0.5) / 5 + a;
        if (v > 1) {
            v = 1 - r.nextDouble() / 100;
        } else if (v < 0) {
            v = r.nextDouble() / 100;
        }
        return v;
    }

    /** 
     * Create synthetic data
     * @param distribution The distribution of the data
    */
    public void create(String distribution) throws IOException{
        FileWriter out = new FileWriter(filename);
        out.write("Relation Data\nID,");
        for (int i = 1; i < num_attribute; i++){
            out.write("Attribute" + i + ",");
        }
        out.write("Attribute" + num_attribute + "\n");
        if (distribution == "uniform") {
            Random r = new Random();
            for (int i = 0; i < num_tuple; i++) {
                out.write((i + 1) + ",");
                for (int j = 0; j < num_attribute - 1; j++) {  
                    out.write(String.format("%.3f", r.nextDouble()) + ",");
                }
                out.write(String.format("%.3f", r.nextDouble()) + "\n");
            }
        } else if (distribution == "correlated") {
            Random r = new Random();
            for (int i = 0; i < num_tuple; i++) {
                out.write((i + 1) + ",");
                double v = r.nextDouble();
                for (int j = 0; j < num_attribute - 1; j++) {
                    out.write(String.format("%.3f", getCorrelated(v)) + ",");
                }
                out.write(String.format("%.3f", getCorrelated(v)) + "\n");
            }
        } else if (distribution == "anti-correlated")  {
            Random r = new Random();
            for (int i = 0; i < num_tuple; i++) {
                out.write((i + 1) + ",");
                double v = r.nextDouble();
                for (int j = 0; j < num_attribute - 1; j++) {  
                    if (j % 2 == 0) {
                        out.write(String.format("%.3f", getCorrelated(v)) + ",");
                    } else {
                        out.write(String.format("%.3f", (1 - getCorrelated(v))) + ",");
                    }
                }
                if ((num_attribute - 1) % 2 == 0) {
                    out.write(String.format("%.3f", getCorrelated(v)) + "\n");
                } else {
                    out.write(String.format("%.3f", (1 - getCorrelated(v))) + "\n");
                }
            }
        }
        out.write("End\n");
        out.close();
    }
}
