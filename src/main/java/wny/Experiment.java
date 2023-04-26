package wny;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import wny.data.Generator;
import wny.entities.Box;
import wny.entities.Constraint;
import wny.entities.Relation;
import wny.entities.Tuple;
import wny.query.Query;
import wny.util.DatabaseParser;

/** 
 * An experiment class which contains experiments in the Why Not Yet? paper.
 * @author Zixuan Chen
*/
public class Experiment {
    /** 
     * Print the configurations of the experiment
     * @param topk The top-k limits for expected tuples
     * @param offset The difference between the expected rank and original rank for the expected tuple
     * @param num_attribute Number of attributes
     * @param num_tuple Number of all tuples in the ranking
    */
    private static void printConfig(int[] topk, int offset, int num_attribute, int num_tuple) {
        String str = "k: (";
        for (int i = 0; i < topk.length; i++) {
            str += topk[i];
        }
        str += ") original rank: (";
        for (int i = 0; i < topk.length; i++) {
            str += (topk[i] + offset);
        }
        str += ") number of attributes: " + num_attribute + " number of expected tuples: " + topk.length + " number of all tuples: " + num_tuple;
        System.out.println(str);
    }

    /** 
     * Get all the tuples to rank from a relation
     * @param relation The given relation
     * @param num_tuple Number of all tuples in the ranking
     * @param num_attribute Number of attributes
     * @return All tuples to rank
    */
    private static ArrayList<Tuple> getTuples(ArrayList<Tuple> relation, int num_tuple, int num_attribute) {
        ArrayList<Tuple> tuples = new ArrayList<Tuple>();
        for (int i = 0; i < num_tuple; i++) {
            Tuple tuple = new Tuple(new String[num_attribute + 1], null);
            for (int j = 0; j <= num_attribute; j++) {
                tuple.values[j] = relation.get(i).values[j];
            }
            tuples.add(tuple);
        }
        return tuples;
    }

    /** 
     * Write integer results into a file
     * @param result The experimental result
     * @param filename The goal file
    */
    private static void write(int[][] result, String filename) throws IOException {
        FileWriter out = new FileWriter(filename);
        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < result[i].length - 1; j++) {
                out.write(result[i][j] + ",");
            }
            out.write(result[i][result[i].length - 1] + "\n");
        }
        out.close();
    }

    /** 
     * Write double results into a file
     * @param result The experimental result
     * @param filename The goal file
    */
    private static void write(double[][] result, String filename) throws IOException {
        FileWriter out = new FileWriter(filename);
        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < result[i].length - 1; j++) {
                out.write(result[i][j] + ",");
            }
            out.write(result[i][result[i].length - 1] + "\n");
        }
        out.close();
    }

    /** 
     * Get the median of an integer array
     * @param a An integer array
     * @return The median of the array
    */
    private static int median(int[] a) {
        Arrays.sort(a);
        int zeros = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == 0) {
                zeros++;
            }
        }
        if (zeros < a.length) {
            if ((a.length - 1 + zeros) % 2 == 0) {
                return a[(a.length - 1 + zeros) / 2];
            } else {
                return (a[(a.length - 1 + zeros) / 2] + a[(a.length - 1 + zeros) / 2 + 1]) / 2;
            }
        } else {
            return 0;
        }
    }

    /** 
     * Get the median of a double array
     * @param a An double array
     * @return The median of the array
    */
    private static double median(double[] a) {
        Arrays.sort(a);
        int zeros = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] == 0) {
                zeros++;
            }
        }
        if (zeros < a.length) {
            if ((a.length - 1 + zeros) % 2 == 0) {
                return a[(a.length - 1 + zeros) / 2];
            } else {
                return (a[(a.length - 1 + zeros) / 2] + a[(a.length - 1 + zeros) / 2 + 1]) / 2;
            }
        } else {
            return 0;
        }
    }

    /** 
     * A case study in Sec 6.2 of the paper, studying how to rank Luka Dončić into top-10
    */
    public static void case_study() throws Exception {
        String input_file = "data/stats.csv";
        DatabaseParser db_parser = new DatabaseParser(null);
        List<Relation> database = db_parser.parse_file(input_file);

        ArrayList<Tuple> relation = database.get(0).tuples;

        String solver = "gurobi";
        int[] topk = {10};
        int num_attribute = 5;
        int num_tuple = relation.size();
        ArrayList<Tuple> tuples = getTuples(relation, num_tuple, num_attribute);
        ArrayList<Tuple> expected_tuples = new ArrayList<Tuple>();
        expected_tuples.add(tuples.get(22));

        Query q;
        q = new Query(solver, "satisfiability", tuples, topk, expected_tuples, 0, true, 1);
        q.run();

        if (q.getSatisfiability()) { 
            q = new Query(solver, "optimization (triangle)", tuples, topk, expected_tuples, 0, true, 1);
            q.run();

            q = new Query(solver, "optimization (cube)", tuples, topk, expected_tuples, 0, true, 1);

            // Adding the following code and changing the topk to 20 generates the Box mentioned in Example 1 in the paper.
            // This is also an example of showing how to use the flexible constraints
            // Constraint c1 = new Constraint(0, "space", 0.35);
            // Constraint c2 = new Constraint(1, "space", 0.35);
            // Constraint c3 = new Constraint(2, "space", 0.35);
            // Constraint c4 = new Constraint(3, "space", 0.2);
            // Constraint c5 = new Constraint(4, "space", 0.1);
            // q.addConstraint(c1);
            // q.addConstraint(c2);
            // q.addConstraint(c3);
            // q.addConstraint(c4);
            // q.addConstraint(c5);

            q.run();
        }
    }

    /** 
     * Performance on BEST, corresponding to Sec 6.3 of the paper.
     * Return the BEST results for tuples originally ranked from 2 to 50.
     * @param binary_search_sat An indicator of whether to use binary search to find the BEST. 
     * True for our algorithm with binary search. False for the Tree method which combines sampling and brute force.
     * 
    */
    public static void best(boolean binary_search_sat) throws Exception {
        String input_file = "data/stats.csv";
        DatabaseParser db_parser = new DatabaseParser(null);
        List<Relation> database = db_parser.parse_file(input_file);

        ArrayList<Tuple> relation = database.get(0).tuples;

        String solver = "gurobi";
        int[] topk = new int[1];
        int num_attribute = 5;
        int num_tuple = relation.size();
        ArrayList<Tuple> tuples = getTuples(relation, num_tuple, num_attribute);

        int[][] result = new int[50][3];
        for (int i = 1; i < 50; i++) {
            System.out.println("Tuple" + i);
            result[i][0] = i;
            ArrayList<Tuple> expected_tuples = new ArrayList<Tuple>();
            expected_tuples.add(tuples.get(i));
            
            long start, end;
            if (binary_search_sat) {
                start = System.currentTimeMillis();
                Query q;
                int low = 0;
                int high = i + 1;
                while (high - low > 1){
                    topk[0] = (high + low) / 2;
                    System.out.println(topk[0]);
                    q = new Query(solver, "satisfiability", tuples, topk, expected_tuples, 0, true, 1);
                    q.run();
                    if (q.getSatisfiability()) {
                        high = topk[0];
                    } else {
                        low = topk[0];
                    }
                }
                end = System.currentTimeMillis();
                result[i][1] = high;
                result[i][2] = (int) (end - start);
            } else {
                start = System.currentTimeMillis();
                Query q = new Query(solver, "best", tuples, topk, expected_tuples, 0, true, 1);
                q.run();
                end = System.currentTimeMillis();
                result[i][1] = q.getBest();
                result[i][2] = (int) (end - start);
            }
        }
        write(result, "result/best.csv");
        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
    }

    /** 
     * Performance on BOX, corresponding to Sec 6.4 of the paper.
     * Four sets of experiments vary the expected rank (𝑘), the original rank of the expected player 𝑟 (𝜌𝑊0(𝑟)), 
     * the number of attributes(𝑚) and the number of the expected tuples (|𝑟|) respectively.
     * By default, we select the tuple with original rank 𝑘 + 10 as the expected tuple, i.e., we explore how this tuple could move up 10 places.
     * For each set of experiment, we conduct 5 runs from 𝜌𝑊0(𝑟) to 𝜌𝑊0(𝑟) + 4 and take the median to reduce individual influences.
     * Besides the weight constraints CUBE and TRIANGLE mentioned in the paper, we provide another constraint PYRAMID here,
     * which is extended from TRIANGLE from sum(w) = 1 to sum(w) <= 1.
    */
    private static void real_experiment() throws Exception {
        String input_file = "data/stats.csv";
        DatabaseParser db_parser = new DatabaseParser(null);
        List<Relation> database = db_parser.parse_file(input_file);

        ArrayList<Tuple> relation = database.get(0).tuples;

        // Default configuration
        String solver = "gurobi";
        String problem = "optimization (cube)";
        int[] topk = {50};
        int num_attribute = 3;
        int num_tuple = relation.size();
        ArrayList<Tuple> tuples = getTuples(relation, num_tuple, num_attribute);
        ArrayList<Tuple> expected_tuples = new ArrayList<Tuple>();
        expected_tuples.add(tuples.get(60));

        Query q;
        int[][] result;

        System.out.println("Varying k");

        result = new int[5][10];
        for (int i = 10; i <= 100; i+= 10) {
            int[] k = new int[1];
            k[0] = i;

            result[0][i/10 - 1] = i;
            int[][] execution_time = new int[4][5];
            for (int j = 0; j < 5; j++) {
                printConfig(k, 10 + j, num_attribute, num_tuple);
                int r = i + 10 + j;
                expected_tuples = new ArrayList<Tuple>();
                expected_tuples.add(tuples.get(r - 1));
            
                q = new Query(solver, "satisfiability", tuples, k, expected_tuples, 0, true, 1);
                execution_time[0][j] = q.run();
                if (q.getSatisfiability()) {
                    q = new Query(solver, "optimization (triangle)", tuples, k, expected_tuples, 0, true, 1);
                    execution_time[1][j] = q.run();
                    q = new Query(solver, "optimization (pyramid)", tuples, k, expected_tuples, 0, true, 1);
                    execution_time[2][j] = q.run();
                    q = new Query(solver, "optimization (cube)", tuples, k, expected_tuples, 0, true, 1);
                    execution_time[3][j] = q.run();
                }
                System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            }
            result[1][i/10 - 1] = median(execution_time[0]);
            result[2][i/10 - 1] = median(execution_time[1]);
            result[3][i/10 - 1] = median(execution_time[2]);
            result[4][i/10 - 1] = median(execution_time[3]);   
        }
        write(result, "result/varying_k.csv");
        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");

        System.out.println("Varying the original rank of the expected tuple");

        result = new int[5][5];
        for (int i = 1; i <= 21; i+= 5) {
            result[0][(i - 1) / 5] = i;
            int[][] execution_time = new int[4][5];

            for (int j = 0; j < 5; j++) {
                printConfig(topk, i + j, num_attribute, num_tuple);
                int r = topk[0] + i + j;
                expected_tuples = new ArrayList<Tuple>();
                expected_tuples.add(tuples.get(r - 1));
            
                q = new Query(solver, "satisfiability", tuples, topk, expected_tuples, 0, true, 1);
                execution_time[0][j] = q.run();
                if (q.getSatisfiability()) { 
                    q = new Query(solver, "optimization (triangle)", tuples, topk, expected_tuples, 0, true, 1);
                    execution_time[1][j] = q.run();
                    q = new Query(solver, "optimization (pyramid)", tuples, topk, expected_tuples, 0, true, 1);
                    execution_time[2][j] = q.run();
                    q = new Query(solver, "optimization (cube)", tuples, topk, expected_tuples, 0, true, 1);
                    execution_time[3][j] = q.run();
                }
                System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            }
            result[1][(i - 1) / 5] = median(execution_time[0]);
            result[2][(i - 1) / 5] = median(execution_time[1]);
            result[3][(i - 1) / 5] = median(execution_time[2]);
            result[4][(i - 1) / 5] = median(execution_time[3]);
        }
        write(result, "result/varying_expected_tuple.csv");
        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        
        System.out.println("Varying the number of attributes");

        result = new int[5][4];
        for (int i = 2; i <= 5; i++) {
            result[0][i - 2] = i;
            int[][] execution_time = new int[4][5];

            for (int j = 0; j < 5; j++) {
                printConfig(topk, 10 + j, i, num_tuple);
                ArrayList<Tuple> ts = getTuples(relation, num_tuple, i);
                int r = topk[0] + 10 + j;
                expected_tuples = new ArrayList<Tuple>();
                expected_tuples.add(ts.get(r - 1));
            
                q = new Query(solver, "satisfiability", ts, topk, expected_tuples, 0, true, 1);
                execution_time[0][j] = q.run();
                if (q.getSatisfiability()) { 
                    q = new Query(solver, "optimization (triangle)", ts, topk, expected_tuples, 0, true, 1);
                    execution_time[1][j] = q.run();
                    q = new Query(solver, "optimization (pyramid)", ts, topk, expected_tuples, 0, true, 1);
                    execution_time[2][j] = q.run();
                    q = new Query(solver, "optimization (cube)", ts, topk, expected_tuples, 0, true, 1);
                    execution_time[3][j] = q.run();
                }
                System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            }
            result[1][i - 2] = median(execution_time[0]);
            result[2][i - 2] = median(execution_time[1]);
            result[3][i - 2] = median(execution_time[2]);
            result[4][i - 2] = median(execution_time[3]);
        }
        write(result, "result/varying_num_attribute.csv");
        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");

        System.out.println("Varying the number of expected tuples");

        result = new int[5][5];
        for (int i = 1; i <= 5; i++) {
            result[0][i - 1] = i;
            int[][] execution_time = new int[4][5];
            
            int[] k = new int[i];
        
            for (int j = 0; j < 5; j++) {
                expected_tuples = new ArrayList<Tuple>();
                for (int l = 0; l < i; l++) {
                    k[l] = 10 + l;
                    int r = k[l] + j - 2;
                    expected_tuples.add(tuples.get(r - 1));
                }
                printConfig(k, j - 2, num_attribute, num_tuple);

                q = new Query(solver, "satisfiability", tuples, k, expected_tuples, 0, true, 1);
                execution_time[0][j] = q.run();
                if (q.getSatisfiability()) { 
                    q = new Query(solver, "optimization (triangle)", tuples, k, expected_tuples, 0, true, 1);
                    execution_time[1][j] = q.run();
                    q = new Query(solver, "optimization (pyramid)", tuples, k, expected_tuples, 0, true, 1);
                    execution_time[2][j] = q.run();
                    q = new Query(solver, "optimization (cube)", tuples, k, expected_tuples, 0, true, 1);
                    execution_time[3][j] = q.run();
                }
                System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            }
            result[1][i - 1] = median(execution_time[0]);
            result[2][i - 1] = median(execution_time[1]);
            result[3][i - 1] = median(execution_time[2]);
            result[4][i - 1] = median(execution_time[3]);
        }
        write(result, "result/varying_num_expected_tuple.csv");
        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
    }

    /** 
     * Scalability on BOX, corresponding to Sec 6.5 of the paper.
     * In this experiment, we vary the size of datasets of three different distributions 
     * to test the scalalability of our method as well as verify the effctiveness of the binary search and clustering techniques.
     * @param distribution The distribution of the synthetic data.
     * 
    */
    private static void synthetic_experiment_techniques(String distribution) throws Exception {
        // Uncomment the following 2 lines to create data
        // Generator g = new Generator(1000000, 3, "data/" + distribution + ".csv");
        // g.create(distribution);

        String input_file = "data/" + distribution + ".csv";
        DatabaseParser db_parser = new DatabaseParser(null);
        List<Relation> database = db_parser.parse_file(input_file);

        ArrayList<Tuple> relation = database.get(0).tuples;

        // Default configuration
        String solver = "gurobi";
        String problem = "optimization (cube)";
        int[] topk = {50};
        int num_attribute = 3;
        ArrayList<Tuple> tuples = new ArrayList<Tuple>();
        ArrayList<Tuple> expected_tuples = new ArrayList<Tuple>();
        
        Query q;
        int[][] result;
        
        System.out.println("Varying number of tuples of " + distribution + " distribution");

        int max = 1000000;
        if (distribution == "anti-correlated") {
            max = 100000;
        }

        result = new int[6][5];
        for (int i = 100; i <= max; i *= 10) {
            result[0][(int)Math.log10(i) - 2] = i;
            int[][] execution_time = new int[5][5];
            tuples = getTuples(relation, i, num_attribute);
            Collections.sort(tuples, Collections.reverseOrder());
            for (int j = 0; j < 5; j++) {
                printConfig(topk, j + 1, num_attribute, i);
                int r = topk[0] + j + 1;
                expected_tuples = new ArrayList<Tuple>();
                expected_tuples.add(tuples.get(r - 1));
            
                q = new Query(solver, "satisfiability", tuples, topk, expected_tuples, 0, true, 1);
                execution_time[0][j] = q.run();
                if (q.getSatisfiability()) {
                    q = new Query(solver, problem, tuples, topk, expected_tuples, 0, true, 1);
                    execution_time[1][j] = q.run();
                    q = new Query(solver, problem, tuples, topk, expected_tuples, 0.5, true, 1);
                    execution_time[2][j] = q.run();
                    q = new Query(solver, problem, tuples, topk, expected_tuples, 0, false, 1);
                    execution_time[3][j] = q.run();
                    q = new Query(solver, problem, tuples, topk, expected_tuples, 0.5, false, 1);
                    execution_time[4][j] = q.run();
                }
                System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            }
            result[1][(int)Math.log10(i) - 2] = median(execution_time[0]);
            result[2][(int)Math.log10(i) - 2] = median(execution_time[1]);
            result[3][(int)Math.log10(i) - 2] = median(execution_time[2]);
            result[4][(int)Math.log10(i) - 2] = median(execution_time[3]);
            result[5][(int)Math.log10(i) - 2] = median(execution_time[4]);
        }
        write(result, "result/techniques_" + distribution + ".csv");
        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
    }

    /** 
     * Tradeoff of clustering, corresponding to Sec 6.5 of the paper.
     * We vary the cluster parameter which is equals to (cluster number / competitor number) 
     * to explore the trade-off between the perimeter ratio and execution time ratio
     * @param distribution The distribution of the synthetic data.
     * 
    */
    public static void clustering(String distribution) throws Exception {
        String input_file = "data/" + distribution + ".csv";
        DatabaseParser db_parser = new DatabaseParser(null);
        List<Relation> database = db_parser.parse_file(input_file);

        ArrayList<Tuple> relation = database.get(0).tuples;

        // Default configuration
        String solver = "gurobi";
        String problem = "optimization (cube)";
        int[] topk = {50};
        int num_attribute = 3;
        int num_tuple = 1000000;
        ArrayList<Tuple> tuples = getTuples(relation, num_tuple, num_attribute);
        ArrayList<Tuple> expected_tuples = new ArrayList<Tuple>();
        
        Query q;
        
        System.out.println("Clustering");

        Collections.sort(tuples, Collections.reverseOrder());
        for (int j = 0; j < 5; j++) {
            printConfig(topk, j + 1, num_attribute, num_tuple);
            int r = topk[0] + j + 1;
            expected_tuples = new ArrayList<Tuple>();
            expected_tuples.add(tuples.get(r - 1));
        
            q = new Query(solver, "satisfiability", tuples, topk, expected_tuples, 0, true, 1);
            q.run();
            if (q.getSatisfiability()) {
                for (int i = 0; i < 7; i++) {
                    System.out.println(0.1 * (i + 1));
                    q = new Query(solver, problem, tuples, topk, expected_tuples, 0.1 * (i + 1), true, 1);
                    q.run();
                }
            }
            System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        }
        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
    }

    /** 
     * Comparison of our algorithm versus the brute force algorithm, corresponding to Sec 6.6-1 of the paper.
     * @param distribution The distribution of the synthetic data.
     * 
    */
    public static void indicator_vs_brute_force(String distribution) throws Exception {
        String input_file = "data/" + distribution + ".csv";
        DatabaseParser db_parser = new DatabaseParser(null);
        List<Relation> database = db_parser.parse_file(input_file);

        ArrayList<Tuple> relation = database.get(0).tuples;

        // Default configuration
        int[] topk = {10};
        int num_attribute = 3;
        ArrayList<Tuple> tuples = new ArrayList<Tuple>();
        ArrayList<Tuple> expected_tuples = new ArrayList<Tuple>();
        
        Query q;
        
        System.out.println("Varying number of tuples of " + distribution + " distribution");

        for (int i = 30; i <= 50; i += 5) {
            tuples = getTuples(relation, i, num_attribute);
            Collections.sort(tuples, Collections.reverseOrder());
            for (int j = 0; j < 5; j++) {
                printConfig(topk, j + 1, num_attribute, i);
                int r = topk[0] + j + 1;
                expected_tuples = new ArrayList<Tuple>();
                expected_tuples.add(tuples.get(r - 1));
            
                q = new Query("gurobi", "satisfiability", tuples, topk, expected_tuples, 0, true, 1);
                q.run();
                System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
            }
        }
        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
    }

    /** 
     * Comparison of our algorithm versus a direct encoding using quantifiers, corresponding to Sec 6.6-2 of the paper.
     * Quantifiers are implemented using a Z3 solver.
     * @param distribution The distribution of the synthetic data.
     * 
    */
    public static void monotonic_vs_quantifier() throws Exception {
        String[] distribution = {"uniform", "correlated", "anti-correlated"};
        double[][] result = new double[45][2];
        int l = 0;

        for (int d = 0; d <= 2; d++) {
            System.out.println(distribution[d]);
            String input_file = "data/" + distribution[d] + ".csv";
            DatabaseParser db_parser = new DatabaseParser(null);
            List<Relation> database = db_parser.parse_file(input_file);

            ArrayList<Tuple> relation = database.get(0).tuples;

            // Default configuration
            int num_attribute = 3;
            int num_tuple = 50;
            ArrayList<Tuple> tuples = getTuples(relation, num_tuple, num_attribute);
            Collections.sort(tuples, Collections.reverseOrder());
            ArrayList<Tuple> expected_tuples = new ArrayList<Tuple>();
            
            Query q;
            
            System.out.println("Monotonic vs quantifier");

            for (int i = 10; i <= 30; i+= 10) {
                int[] k = new int[1];
                k[0] = i;

                for (int j = 0; j < 5; j++) {
                    printConfig(k, 1 + j, num_attribute, num_tuple);
                    int r = i + 1 + j;
                    expected_tuples = new ArrayList<Tuple>();
                    expected_tuples.add(tuples.get(r - 1));
                
                    q = new Query("gurobi", "satisfiability", tuples, k, expected_tuples, 0, true, 1);
                    q.run();
                    if (q.getSatisfiability()) {
                        q = new Query("gurobi", "optimization (cube)", tuples, k, expected_tuples, 0, true, 1);
                        int monotonic_time = q.run();
                        double monotonic_perimeter = q.getBox().getPerimeter();
                        q = new Query("z3 solver", "direct optimization (cube)", tuples, k, expected_tuples, 0, true, 1);
                        int quantifier_time = q.run();
                        double quantifier_perimeter = q.getBox().getPerimeter();
                        result[l][0] = (double) monotonic_time / quantifier_time;
                        result[l][1] = (double) monotonic_perimeter / quantifier_perimeter;
                        l++;
                    } else {
                        result[l][0] = 0.0;
                        result[l][1] = 0.0;
                        l++;
                    }
                    System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
                }
            }
            write(result, "result/quantifier.csv");
            System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
        }
    }

    /** 
     * Comparison of using volume or perimeter as the measure, corresponding to Sec 6.6-3 of the paper.
     * @param distribution The distribution of the synthetic data.
     * 
    */
    public static void perimeter_vs_volume() throws Exception {
        // Generator g = new Generator(10000, 5, "data/uniform-5d.csv");
        // g.create("uniform");

        String input_file = "data/uniform-5d.csv";
        DatabaseParser db_parser = new DatabaseParser(null);
        List<Relation> database = db_parser.parse_file(input_file);

        ArrayList<Tuple> relation = database.get(0).tuples;

        // Default configuration
        int n = 100;
        ArrayList<Tuple> tuples = new ArrayList<Tuple>();
        ArrayList<Tuple> expected_tuples = new ArrayList<Tuple>();
        
        Query q;
        double[][] result = new double[4][6];
        
        System.out.println("Perimeter vs volume experiment");

        for (int m = 2; m <= 5; m++) {
            for (int k = 5; k <= 30; k += 5) {
                int[] topk = new int[1];
                topk[0] = k;

                double[] ratio = new double[5];
                for (int i = 0; i < 5; i++) {
                    printConfig(topk, i + 1, m, n);
                    int r = k + i + 1;
                    tuples = getTuples(relation, n, m);
                    Collections.sort(tuples, Collections.reverseOrder());
                    expected_tuples = new ArrayList<Tuple>();
                    expected_tuples.add(tuples.get(r - 1));

                    System.out.println("Optimal perimeter");
                    q = new Query("gurobi", "optimization (cube)", tuples, topk, expected_tuples, 0, true, 1);
                    int perimeter_time = q.run();
                    System.out.println("Optimal volume");
                    q = new Query("gurobi", "optimization (cube)", tuples, topk, expected_tuples, 0, true, 0);
                    int volume_time = q.run();
                    if (perimeter_time == 0.0) {
                        ratio[i] = 0.0;
                    } else {
                        ratio[i] = (double) volume_time / perimeter_time;
                    }
                }
                result[m-2][(k-5)/5] = median(ratio);
            }
        }
        write(result, "result/perimeter_vs_volume.csv");
        System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
    }
    public static void main(String args[]) throws Exception 
    {
        case_study();

        best(false);

        real_experiment();
        
        synthetic_experiment_techniques("uniform");
        synthetic_experiment_techniques("correlated");
        synthetic_experiment_techniques("anti-correlated");

        clustering("uniform");

        indicator_vs_brute_force("uniform");
        monotonic_vs_quantifier();
        perimeter_vs_volume();
    }
}
