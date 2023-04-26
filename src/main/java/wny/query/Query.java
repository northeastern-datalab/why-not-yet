package wny.query;

import java.util.ArrayList;

import wny.entities.Box;
import wny.entities.Constraint;
import wny.entities.Tuple;
import wny.solver.Solver;
import wny.solver.Z3Solver;
import wny.solver.GurobiOptimizer;

/** 
 * A query class which contains everything about a why-not-yet question
 * @author Zixuan Chen
*/
public class Query {
    private Solver solver;
    private int[] topk;
    private String problem;
    private double clustered; // 0 for w/o clustering, x in (0,1) for (num_inequalities * x) clusters 
    private boolean precise; // true for w/o binary search, false for w/ binary search
    private int measure; // 0 for volume, 1 for perimeter
    private int execution_time;
    private Box box;
    private boolean satisfiability;
    private int best;

    /** 
     * @param solver The solver used for this question. The options are Gurobi optimizer, z3 solver.
     * @param problem The problem to solve. The options are SAT, BEST, BOX(TRIANGLE, PYRAMID, CUBE, Direct encoding for CUBE)
     * @param tuples The list of all tuples to rank
     * @param topk The top-k limits for expected tuples
     * @param expcted_tuples The list of all expecetd tuples
     * @param clustered An indicator of whether to use cluster
     * @param precise An indicator of whether to use binary search to get an approximate result
     * @param measure The optimization measure
    */
    public Query (String solver, String problem, ArrayList<Tuple> tuples,
    int[] topk, ArrayList<Tuple> expected_tuples, double clustered, boolean precise, int measure) throws Exception {
        System.out.println("This query uses " + solver + " to solve the " + problem + " problem of a why-not-yet question.");

        execution_time = 0;

        System.out.println("Initializing a " + solver +": ");
        long start = System.currentTimeMillis();
        if (solver == "gurobi") {
            this.solver = new GurobiOptimizer(tuples, expected_tuples);
        } else if (solver == "z3 solver"){
            this.solver = new Z3Solver(tuples, expected_tuples);
        }
        long end = System.currentTimeMillis();
        System.out.println("Initialization time: " + (end - start) + "ms");
        execution_time += end - start;
        System.out.println();

        this.problem = problem;
        this.topk = topk;
        this.clustered = clustered;
        this.precise = precise;
        this.measure = measure;
	}

    /** 
     * Add one flexible constraint
     * @param c The constraint to be added
    */
    public void addConstraint(Constraint c) {
        solver.addConstraint(c);
    }

    /** 
     * Add a set of flexible constraints
     * @param list The constraints to be added
    */
    public void addConstraints(ArrayList<Constraint> list) {
        for (Constraint c : list) {
            solver.addConstraint(c);
        }
    }
    
    /** 
     * Run the solver for the given problem
    */
    public int run() throws Exception {
        long start, end;
        satisfiability = true;

        if (clustered > 0) {
            start = System.currentTimeMillis();
            solver.cluster(clustered);
            end = System.currentTimeMillis();
            System.out.println("Clustering time: " + (end - start) + "ms");
            execution_time += end - start;
            System.out.println();
            satisfiability = solver.solve_satisfiability(topk);
            System.out.println("Satisfiable after clustering? " + satisfiability);
        }

        System.out.println("Solving the problem of " + problem + ": ");
        if (problem == "satisfiability") {
            // double[] weights = {0.2, 0.2, 0.2, 0.2, 0.2};
            start = System.currentTimeMillis();
            satisfiability = solver.solve_satisfiability(topk);
            end = System.currentTimeMillis();
            System.out.println("Satisfiable? " + satisfiability);
            System.out.println("Satisfiability solver execution time: " + (end - start) + "ms");
            System.out.println();
            execution_time += end - start;
        } else if (problem == "best") {
            // The following code is TREE implementation for the BEST experiment, corresponding to Sec 6.3 of the paper
            start = System.currentTimeMillis();
            best = solver.solve_best_brute_force_sampling();
            end = System.currentTimeMillis();
            System.out.println("Best ranking: " + best);
            System.out.println("BEST execution time: " + (end - start) + "ms");
            System.out.println();
            execution_time += end - start;
        } else {
            if (satisfiability) {
                start = System.currentTimeMillis();
                if (problem == "optimization (triangle)") {
                    box = solver.optimize_box_triangle(topk, precise, measure);
                } else if (problem == "optimization (pyramid)") {
                    box = solver.optimize_box_pyramid(topk, precise, measure);
                } else if (problem == "optimization (cube)") {
                    box = solver.optimize_box_cube(topk, precise, measure);
                } else {
                    box = solver.optimize_box_cube_forall(topk, precise);
                }
                end = System.currentTimeMillis();
                box.print();
                System.out.println("Optimizer execution time: " + (end - start) + "ms");
                execution_time += end - start;
                if (!box.valid()) {
                    execution_time = 0;
                }
            } else {
                execution_time = 0;
            }
        }
        System.out.println("Query finished");
        System.out.println();
        return execution_time;
    }
    
    /** 
     * @return The BOX result
    */
    public Box getBox() {
        return box;
    }
    
    /** 
     * @return The SAT result
    */
    public boolean getSatisfiability() {
        return satisfiability;
    }
    
    /** 
     * @return The BEST result
    */
    public int getBest() {
        return best;
    }
}