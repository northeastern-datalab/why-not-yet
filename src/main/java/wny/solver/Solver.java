package wny.solver;

import java.util.ArrayList;

import gurobi.GRBException;
import weka.clusterers.*;
import weka.core.*;
import wny.entities.Constraint;
import wny.entities.Tuple;
import wny.entities.Box;

/** 
 * A solver class which implements all pre-processing tasks
 * It needs to be extended to be some specific solver or optimizer
 * @author Zixuan Chen
*/
public class Solver {

    protected class Question {
        protected Tuple expected_tuple;
        protected ArrayList<ArrayList<Double>> inequalities;
        protected int num_dominators;
        protected int num_dominatees;
        protected int num_competitors;
        protected int num_inequalities;
        protected int k;
    }

    protected ArrayList<Tuple> tuples;
    protected int num_attributes;
    protected ArrayList<Question> questions;
    protected ArrayList<Constraint> constraints;
    protected boolean clustered;

    /** 
     * Construct an empty solver to which a real solver can be assigned
    */
    public Solver () {

	}

    /** 
     * Construct the solver
     * @param tuples All tuples of a relation
     * @param expected_tuple The expected tuple in the why-not-yet question
    */
    public Solver (ArrayList<Tuple> tuples, ArrayList<Tuple> expected_tuples) throws Exception {
		this.tuples = tuples;
        constraints = new ArrayList<Constraint>();
        clustered = false;

        questions = new ArrayList<Question>();
        for (Tuple t : expected_tuples) {
            Question q = new Question();
            q.expected_tuple = t;
            questions.add(q);
        }

        initialize();
	}

    /** 
     * Initialize to get all inequalities from the data
     * Each inequality compares the expected tuple with a competitor to check whether the competitor is a dominator or dominatee to remove
    */
    private void initialize() {
        for (Question q : questions) {
            q.inequalities = new ArrayList<ArrayList<Double>>();
            for (int i = 0; i < tuples.size(); i++) {
                Tuple t = tuples.get(i);
                if (q.expected_tuple.isDominating(t) == 1) {
                    q.num_dominatees++;
                } else if (q.expected_tuple.isDominating(t) == -1) {
                    q.num_dominators++;
                } else {
                    q.num_competitors++;
                    ArrayList<Double> l = new ArrayList<Double>();
                    for (int j = 1; j < t.values.length; j++) {
                        l.add(Double.valueOf(q.expected_tuple.values[j]) - Double.valueOf(t.values[j]));
                    }
                    q.inequalities.add(l);
                }
            }
            q.num_inequalities = q.num_competitors;
        }
        num_attributes = tuples.get(0).values.length - 1;
        print();
    }

    /** 
     * Print statistics
    */
    private void print() {
        System.out.println(questions.size() + " expected tuples:");
        for (Question q : questions) {
            System.out.println("Tuple " + q.expected_tuple.values[0]);
            System.out.println("Dominator number: " + q.num_dominators);
            System.out.println("Dominatee number: " + q.num_dominatees);
            System.out.println("Competitor number: " + q.num_competitors);
        }
    }

    /**
     * @param topk The k in the why-not-yet question
     * @return The number of how many competitors can win the expected tuple
    */
    protected int getTopKConstraint(int topk, int num_dominators) {
        return topk - num_dominators - 1;
    }

    /** 
     * Add one flexible constraint
     * @param c The constraint to be added
    */
    public void addConstraint(Constraint c) {
        constraints.add(c);
    }

    /** 
     * Cluster to accelerate the computation
    */
    public void cluster(double cluster_parameter) throws Exception {
        ArrayList<Attribute> attributes = new ArrayList<Attribute>();
        for (int i = 1; i <= num_attributes; i++) {
            Attribute a = new Attribute("attribute" + i);
            attributes.add(a);
        }

        for (Question q : questions) {
            Instances data = new Instances("data", attributes, q.num_inequalities); 

            for (int i = 0; i < q.num_inequalities; i++) {
                Instance instance = new DenseInstance(num_attributes);
                for (int j = 0; j < num_attributes; j++) {
                    instance.setValue(j, q.inequalities.get(i).get(j));
                }
                data.add(instance);
            }
            
            SimpleKMeans cluster = new SimpleKMeans();
            int num_clusters = (int) (q.num_inequalities * cluster_parameter);
            cluster.setPreserveInstancesOrder(true);
            cluster.setNumClusters(num_clusters);
            cluster.setMaxIterations(1);

            cluster.buildClusterer(data);
            
            int[] assignments = cluster.getAssignments();

            ArrayList<ArrayList<Double>> clustered_inequalites = new ArrayList<ArrayList<Double>>();
            for (int i = 0; i < num_clusters; i++) {
                ArrayList<Double> l = new ArrayList<Double>();
                for (int j = 0; j < num_attributes; j++) {
                    l.add(100.0);
                }
                l.add(0.0);
                clustered_inequalites.add(l);
            }

            for (int i = 0; i < q.num_inequalities; i++) {
                ArrayList<Double> l = clustered_inequalites.get(assignments[i]);
                for (int j = 0; j < num_attributes; j++) {
                    l.set(j, Math.min(l.get(j), q.inequalities.get(i).get(j)));
                }
                l.set(num_attributes, l.get(num_attributes) + 1);
            }

            q.inequalities = clustered_inequalites;
            q.num_inequalities = num_clusters;
        }

        clustered = true;
    }

    /** 
     * Turn an object in the form of "a / b" into a Double
     * @param e The object
     * @return The double.
    */
    protected Double toDouble(Object e) {
        String[] str = e.toString().split("/");
        if (str.length > 1) {
            Double a = Double.parseDouble(str[0]);
            Double b = Double.parseDouble(str[1]);
            return a / b;
        } else {
            return Double.parseDouble(str[0]);
        }
    }

    /** 
     * The following methods are empty and designed to be implemented in a child class
     * @throws GRBException
    */
    public boolean solve_satisfiability(int[] topk) throws GRBException {
        return false;
    }

    public boolean solve_satisfiability_brute_force(int[] topk) throws GRBException {
        return false;
    }
    
    public int solve_best_brute_force_sampling() throws GRBException {
        return -1;
    }

    public Box optimize_box_triangle(int[] topk, boolean precise, int measure) throws GRBException {
        return new Box(-1.0);
    }

    public Box optimize_box_pyramid(int[] topk, boolean precise, int measure) throws GRBException {
        return new Box(-1.0);
    }

    public Box optimize_box_cube(int[] topk, boolean precise, int measure) throws GRBException {
        return new Box(-1.0);
    }

    public Box optimize_box_cube_forall(int[] topk, boolean precise) throws GRBException {
        return new Box(-1.0);
    }
}