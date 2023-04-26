package wny.solver;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import gurobi.*;
import wny.entities.Box;
import wny.entities.Constraint;
import wny.entities.Treenode;
import wny.entities.Tuple;

/** 
 * An optimizer using the gurobi library
 * @author Zixuan Chen
*/
public class GurobiOptimizer extends Solver {
    private GRBEnv env;
    private GRBModel model;
    int count = 0;
    
    /** 
     * @param tuples All tuples of a relation
     * @param expected_tuples The expected tuples in the why-not-yet question
    */
    public GurobiOptimizer(ArrayList<Tuple> tuples, ArrayList<Tuple> expected_tuples) throws Exception {
        super(tuples, expected_tuples);
    }

    /** 
     * Set up the environment and model
     * @timeout The time limit for the model
    */
    private void setup(int timeout) throws GRBException {
        env = new GRBEnv(true);
        env.set("logFile","gurobi.log");
        env.set(GRB.IntParam.OutputFlag, 0);
        env.start();

        model = new GRBModel(env);
        model.set(GRB.IntParam.LogToConsole, 0);
        if (timeout != 0) { 
            model.set(GRB.DoubleParam.TimeLimit, timeout);
        }
    }
    
    /** 
     * Dispose the environment and model
    */
    private void close() throws GRBException {
        model.dispose();
        env.dispose();
    }

    /** 
     * Apply flexible constraints
     * @param X All variables of the problem (lower bounds and upper bounds)
     * @throws GRBException
    */
    private void apply_constraints(GRBVar[] V) throws GRBException {
        for (Constraint c:constraints) {
            if (c.type == "min") {
                model.addConstr(V[c.attribute * 2], GRB.GREATER_EQUAL, c.value, "min" + c.attribute);
            } else if (c.type == "max") {
                model.addConstr(V[c.attribute * 2 + 1], GRB.LESS_EQUAL, c.value, "max" + c.attribute);
            } else if (c.type == "space") {
                GRBLinExpr expr = new GRBLinExpr();
                expr.addTerm(-1.0, V[c.attribute * 2]);
                expr.addTerm(1.0, V[c.attribute * 2 + 1]);
                model.addConstr(expr, GRB.GREATER_EQUAL, c.value, "space" + c.attribute);
            }
        }
    }

    /** 
     * Turn the output model into a box
     * @param perimeter The perimeter of this box
     * @param V All variables of the problem (lower bounds and upper bounds)
     * @return The box
    */
    private Box getBox(Double perimeter, GRBVar[] V) throws GRBException {
        Box b = new Box(perimeter);

        for (int i = 0; i < V.length; i++) {
            b.addBound(String.format("%.5f", V[i].get(GRB.DoubleAttr.X)));
        }

        return b;
    }

    /** 
     * Solve the why-not-yet best problem using the brute force way with sampling
     * @param topk The top-k limits for expected tuples
     * @return The BEST result
    */
    public int solve_best_brute_force_sampling() throws GRBException {
        setup(0);

        int num_used_attributes = num_attributes;
        
        Question q = questions.get(0);

        Queue<Treenode> queue = new LinkedList<Treenode>();
        queue.add(new Treenode(0, new ArrayList<ArrayList<Double>>(), new ArrayList<ArrayList<Double>>()));

        GRBLinExpr expr = new GRBLinExpr();

        int count = 0;
        int ranking = q.num_competitors + 1;
        long start = System.currentTimeMillis();
        while (true) {
            // long end = System.currentTimeMillis();
            count++;
            // if (end - start > 6000) {
            //     System.out.println(count);
            //     return ranking;
            // }
            if (count % 10000 == 0) {
                System.out.println(count);
            }
            model.dispose();
            model = new GRBModel(env);
            model.set(GRB.IntParam.LogToConsole, 0);
            GRBLinExpr e = new GRBLinExpr();
            GRBVar V[] = new GRBVar[num_used_attributes];
            for (int i = 0; i < num_used_attributes; i++) {
                V[i] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "x" + String.valueOf(i));
                e.addTerm(1.0, V[i]);
            }
            model.addConstr(e, GRB.EQUAL, 1.0, "one");

            Treenode t = queue.poll();
            if (t == null) {
                long end = System.currentTimeMillis();
                System.out.println(end - start);
                System.out.println(count);
                return ranking;
            }

            ArrayList<ArrayList<Double>> win_inequalities = t.getInequalities(true);
            ArrayList<ArrayList<Double>> lose_inequalities = t.getInequalities(false);

            for (int i = 0; i < win_inequalities.size(); i++) {
                expr = new GRBLinExpr();
                for (int j = 0; j < num_used_attributes; j++) {
                    Double c = win_inequalities.get(i).get(j);
                    expr.addTerm(c, V[j]);
                }
                model.addConstr(expr, GRB.GREATER_EQUAL, 0.0, "win_constraint" + i);
            }
            for (int i = 0; i < lose_inequalities.size(); i++) {
                expr = new GRBLinExpr();
                for (int j = 0; j < num_used_attributes; j++) {
                    Double c = lose_inequalities.get(i).get(j);
                    expr.addTerm(c, V[j]);
                }
                model.addConstr(expr, GRB.LESS_EQUAL, 0.0, "lose_constraint" + i);
            }

            model.optimize();

            int status = model.get(GRB.IntAttr.Status);
            if (status == 2) {
                double[] weights = new double[V.length];
                for (int j = 0; j < V.length; j++) {
                    weights[j] = V[j].get(GRB.DoubleAttr.X);
                }
                int lose = 0;
                for (int j = 0; j < q.num_inequalities; j++) {
                    double value = 0;
                    for (int p = 0; p < num_used_attributes; p++) {
                        value += weights[p] * q.inequalities.get(j).get(p);
                    }
                    if (value < 0) {
                        lose++;
                        if (lose >= ranking - 1) {
                            break;
                        }
                    }
                }
                if (lose < ranking - 1) {
                    ranking = lose + 1;
                    if (ranking == 1) {
                        return ranking;
                    }
                }
                int i = t.which();
                if (i < q.num_inequalities) {
                    Treenode node_right = new Treenode(t);
                    node_right.next();
                    node_right.addInequality(q.inequalities.get(i), true);
                    queue.add(node_right);

                    Treenode node_left = new Treenode(t);
                    node_left.next();
                    node_left.addInequality(q.inequalities.get(i), false);
                    queue.add(node_left);
                }
            } else if (status == 3) {
                continue;
            }
        }
    }

    /** 
     * Recursive function for solving the why-not-yet satisfiability problem using the brute force way
     * It is only implemented for one expected tuple because the brute force algorithm is not our main focus
     * @return The SAT result
    */
    private boolean choose(Question q, int length, int i, int num_used_attributes, GRBVar[] V) throws GRBException {
        if (i == q.num_inequalities) {
            count++;
            model.optimize();

            int status = model.get(GRB.IntAttr.Status);

            if (status == 2) {
                for (int j = 0; j < V.length; j++) {
                    System.out.print(String.format("%.5f", V[j].get(GRB.DoubleAttr.X)) + " ");
                }
                System.out.println();
                return true;
            } else if (status == 3) {
                return false;
            }
        }
        if (length > 0) {
            GRBLinExpr expr = new GRBLinExpr();
            for (int j = 0; j < num_used_attributes; j++) {
                Double c = q.inequalities.get(i).get(j);
                expr.addTerm(c, V[j]);
            }
            model.addConstr(expr, GRB.GREATER_EQUAL, 0.0, "constraint" + length);
            if (choose(q, length - 1, i + 1, num_used_attributes, V)) {
                return true;
            } else if (length == q.num_inequalities - i) {
                model.remove(model.getConstrByName("constraint" + length));
                return false;
            } else {
                model.remove(model.getConstrByName("constraint" + length));
                return choose(q, length, i + 1, num_used_attributes, V);
            }
        } else {
            return choose(q, length, i + 1, num_used_attributes, V);
        }
    }
    
    /** 
     * Solve the why-not-yet satisfiability problem using the brute force way
     * The brute force algorithm simply tries out every possible ranking that can rank the expected tuple into top-k until one satisfiable ranking is found
     * It is only implemented for one expected tuple because the brute force algorithm is not our main focus
     * @param topk The top-k limits for expected tuples
     * @return The SAT result
    */
    public boolean solve_satisfiability_brute_force(int[] topk) throws GRBException {
        setup(0);

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            q.k = getTopKConstraint(topk[i], q.num_dominators);
            if (q.k < 0) {
                System.out.println("UNSATISFIABLE");
                return false;
            }
        }

        int num_used_attributes = num_attributes;
        
        GRBLinExpr expr = new GRBLinExpr();
        GRBVar V[] = new GRBVar[num_used_attributes];
        for (int i = 0; i < num_used_attributes; i++) {
            V[i] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "x" + String.valueOf(i));
            expr.addTerm(1.0, V[i]);
        }
        model.addConstr(expr, GRB.EQUAL, 1.0, "one");

        Question q = questions.get(0);

        if (choose(q, q.num_inequalities - q.k, 0, num_used_attributes, V)) {
            System.out.println("SATISFIABLE" + count);
            close();
            return true;
        } else {
            System.out.println("UNSATISFIABLE" + count);
            close();
            return false;
        }
    }

    /** 
     * Solve the why-not-yet satisfiability problem using indicators
     * Use 1 TRIANGLE weight constraint for all questions, q.num_ineqaulities indicators and 1 constraint on the sum of the indicators for each question
     * @param topk The top-k limits for expected tuples
     * @return The SAT result
    */
    public boolean solve_satisfiability(int[] topk) throws GRBException {
        setup(0);

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            q.k = getTopKConstraint(topk[i], q.num_dominators);
            if (q.k < 0) {
                System.out.println("UNSATISFIABLE");
                return false;
            }
        }

        int num_used_attributes = num_attributes;
        
        // Add the TRIANGLE weight constraint, which is one way to prevent all-zero weights as the answer
        GRBLinExpr expr = new GRBLinExpr();
        GRBVar V[] = new GRBVar[num_used_attributes];
        for (int i = 0; i < num_used_attributes; i++) {
            V[i] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "x" + String.valueOf(i));
            expr.addTerm(1.0, V[i]);
        }
        model.addConstr(expr, GRB.EQUAL, 1.0, "one");

        for (Question q : questions) {
            GRBVar indicators[] = new GRBVar[q.num_inequalities];
            double[] indicator_weights = new double[q.num_inequalities];
            // Add indicators
            for (int i = 0; i < q.num_inequalities; i++) {
                expr = new GRBLinExpr();
                for (int j = 0; j < num_used_attributes; j++) {
                    Double c = q.inequalities.get(i).get(j);
                    expr.addTerm(c, V[j]);
                }
                indicators[i] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "indicator" + i);
                if (!clustered) {
                    indicator_weights[i] = 1;
                } else {
                    indicator_weights[i] = q.inequalities.get(i).get(num_attributes);
                }
                model.addGenConstrIndicator(indicators[i], 1, expr, GRB.GREATER_EQUAL, 0.0, "constraint" + i);
            }
            // Add the constraint on the sum of the indicators
            expr = new GRBLinExpr();
            expr.addTerms(indicator_weights, indicators);
            model.addConstr(expr, GRB.EQUAL, q.num_competitors - q.k, "indicators");
        }

        model.optimize();

        int status = model.get(GRB.IntAttr.Status);

        if (status == 2) {
            System.out.println("OPTIMAL");
            for (int i = 0; i < V.length; i++) {
               System.out.print(String.format("%.5f", V[i].get(GRB.DoubleAttr.X)) + " ");
            }
            System.out.println();
            close();
            return true;
        } else if (status == 3) {
            System.out.println("INFEASIBLE");
            close();
        }

        return false;
    }

    /** 
     * Utilize binary search to optimize
     * @param V All variables of the problem (lower bounds and upper bounds)
     * @param num_attributes The number of used attributes
     * @param perimeter_high The upper bound of the perimeter
     * @return The BOX result
    */
    private Box binary_search(GRBVar V[], int num_attributes, double perimeter_high) throws GRBException {
        GRBLinExpr expr = new GRBLinExpr();
        for (int i = 0; i < num_attributes; i++) {
            expr.addTerm(-1.0, V[i * 2]);
            expr.addTerm(1.0, V[i * 2 + 1]);
        }
        Double perimeter_low = 0.0;
        Double perimeter = perimeter_high - 0.00000001;

        int status;
        Box b = new Box(-1.0);

        while(true) {
            if (perimeter_high - perimeter_low < 0.01 || perimeter_high < 0.00001) {
                break;
            }
            
            System.out.print(String.format("%.5f", perimeter));
            System.out.print(" ");

            model.addConstr(expr, GRB.EQUAL, perimeter, "perimeter");
            model.optimize();

            status = model.get(GRB.IntAttr.Status);
    
            if (status == 2) {
                System.out.println("OPTIMAL");
                b = getBox(perimeter, V);
                perimeter_low = perimeter;
            } else if (status == 3) {
                System.out.println("INFEASIBLE");
                perimeter_high = perimeter;
            } else {
                System.out.println("TIMEOUT");
                perimeter_high = perimeter;
            }
            perimeter = (perimeter_high - perimeter_low) / 2 + perimeter_low;
            model.remove(model.getConstrByName("perimeter"));
        }

        return b;
    }

    /** 
     * Optimize perimeter
     * Use 1 constraint on the perimeter to avoid the output of an empty box
     * @param V All variables of the problem (lower bounds and upper bounds)
     * @param num_used_attributes The number of used attributes
    */
    public void optimize_perimeter(GRBVar V[], int num_used_attributes) throws GRBException {
        GRBLinExpr expr = new GRBLinExpr();
        for (int i = 0; i < num_used_attributes; i++) {
            expr.addTerm(-1.0, V[i * 2]);
            expr.addTerm(1.0, V[i * 2 + 1]);
        }
        model.addConstr(expr, GRB.GREATER_EQUAL, Math.pow(10, -5), "Non-zero");
        model.setObjective(expr, GRB.MAXIMIZE);
        model.optimize();
    }
    
    /** 
     * Optimize volume
     * Use 1 quadratic constraint on the volume to avoid the output of an empty box
     * @param V All variables of the problem (lower bounds and upper bounds)
     * @param num_used_attributes The number of used attributes
    */
    public void optimize_volume(GRBVar V[], int num_used_attributes) throws GRBException {
        model.set(GRB.IntParam.NonConvex, 2);

        GRBQuadExpr quadexpr = new GRBQuadExpr();
        GRBVar one = model.addVar(1.0, 1.0, 0.0, GRB.CONTINUOUS, "one");
        quadexpr.addTerm(1.0, one);
        GRBVar U[] = new GRBVar[num_used_attributes];
        GRBLinExpr expr;
        for (int i = 0; i < num_used_attributes; i++) {
            expr = new GRBLinExpr();
            expr.addTerm(-1.0, V[i * 2]);
            expr.addTerm(1.0, V[i * 2 + 1]);
            U[i] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "x" + String.valueOf(i) + "range");
            model.addConstr(U[i], GRB.EQUAL, expr, "range" + i);
            GRBVar u = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, null);
            model.addQConstr(u, GRB.EQUAL, quadexpr, null);
            quadexpr = new GRBQuadExpr();
            quadexpr.addTerm(1.0, u, U[i]);
        }
        model.addQConstr(quadexpr, GRB.GREATER_EQUAL, Math.pow(10, -5), "Non-zero");
        model.setObjective(quadexpr, GRB.MAXIMIZE);
        model.optimize();
    }

    /** 
     * Solve the why-not-yet box problem with the TRIANGLE weight constraint
     * Use 1 TRIANGLE weight constraint and (num_attributes - 1) constraints on (upper bound - lower bound) for all questions, 
     * q.num_ineqaulities indicators and 1 constraint on the sum of the indicators for each question
     * @param topk The top-k limits for expected tuples
     * @return The BOX result
    */
    public Box optimize_box_triangle(int[] topk, boolean precise, int measure) throws GRBException {
        if (precise) {
            setup(0);
        } else {
            setup(60);
        }

        Box b = new Box(-1.0);

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            q.k = getTopKConstraint(topk[i], q.num_dominators);
            if (q.k < 0) {
                System.out.println("UNSATISFIABLE");
                return null;
            }
        }

        int num_used_attributes = num_attributes - 1;

        GRBVar V[] = new GRBVar[num_used_attributes * 2];
        for (int i = 0; i < num_used_attributes; i++) {
            V[i * 2] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "x" + String.valueOf(i) + "lower");
            V[i * 2 + 1] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "x" + String.valueOf(i) + "upper");
        }

        GRBLinExpr expr;

        // Add constraints to make sure the upper bound is larger than or equal to the lower bound on each attribute 
        for (int i = 0; i < num_used_attributes; i++) {
            expr = new GRBLinExpr();
            expr.addTerm(-1.0, V[i * 2]); 
            expr.addTerm(1.0, V[i * 2 + 1]); 
            model.addConstr(expr, GRB.GREATER_EQUAL, 0.0, "bound" + String.valueOf(i));
        }

        // Add the TRIANGLE constraint
        expr = new GRBLinExpr();
        for (int i = 0; i < num_used_attributes; i++) {
            expr.addTerm(1.0, V[i * 2 + 1]); 
        }
        model.addConstr(expr, GRB.LESS_EQUAL, 1.0, "triangle");
        
        for (Question q : questions) {
            GRBVar indicators[] = new GRBVar[q.num_inequalities];
            double[] indicator_weights = new double[q.num_inequalities];
            // Add indicators
            for (int i = 0; i < q.num_inequalities; i++) {
                expr = new GRBLinExpr();
                for (int j = 0; j < num_used_attributes; j++) {
                    Double c = q.inequalities.get(i).get(j) - q.inequalities.get(i).get(num_used_attributes);
                    if (c >= 0) {
                        expr.addTerm(c, V[j * 2]);
                    } else {
                        expr.addTerm(c, V[j * 2 + 1]);
                    }
                }
                indicators[i] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "indicator" + i);
                if (!clustered) {
                    indicator_weights[i] = 1;
                } else {
                    indicator_weights[i] = q.inequalities.get(i).get(num_attributes);
                }
                model.addGenConstrIndicator(indicators[i], 1, expr, GRB.GREATER_EQUAL, -q.inequalities.get(i).get(num_used_attributes), "constraint" + i);
            }
            // Add the constraint on the sum of the indicators
            expr = new GRBLinExpr();
            expr.addTerms(indicator_weights, indicators);
            model.addConstr(expr, GRB.EQUAL, q.num_competitors - q.k, "indicators");
        }

        apply_constraints(V);

        if (precise) {
            if (measure == 0) {
                optimize_volume(V, num_used_attributes);
            } else if (measure == 1) {
                optimize_perimeter(V, num_used_attributes);
            }
            int status = model.get(GRB.IntAttr.Status);
    
            if (status == 2) {
                System.out.println("OPTIMAL");
                b = getBox(model.get(GRB.DoubleAttr.ObjVal), V);
            } else if (status == 3) {
                System.out.println("INFEASIBLE");
            }  
        } else {
            Double perimeter_high = 1.0;

            b = binary_search(V, num_used_attributes, perimeter_high);
        }

        close();

        return b;
    }

    /** 
     * Solve the why-not-yet box problem with the PYRAMID weight constraint
     * Use 1 PYRAMID weight constraint and num_attributes constraints on (upper bound - lower bound) for all questions, 
     * q.num_ineqaulities indicators and 1 constraint on the sum of the indicators for each question
     * @param topk The top-k limits for expected tuples
     * @return The BOX result
    */
    public Box optimize_box_pyramid(int[] topk, boolean precise, int measure) throws GRBException {
        if (precise) {
            setup(0);
        } else {
            setup(60);
        }

        Box b = new Box(-1.0);

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            q.k = getTopKConstraint(topk[i], q.num_dominators);
            if (q.k < 0) {
                System.out.println("UNSATISFIABLE");
                return null;
            }
        }

        int num_used_attributes = num_attributes;

        GRBVar V[] = new GRBVar[num_used_attributes * 2];
        for (int i = 0; i < num_used_attributes; i++) {
            V[i * 2] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "x" + String.valueOf(i) + "lower");
            V[i * 2 + 1] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "x" + String.valueOf(i) + "upper");
        }

        GRBLinExpr expr;

        // Add constraints to make sure the upper bound is larger than or equal to the lower bound on each attribute 
        for (int i = 0; i < num_used_attributes; i++) {
            expr = new GRBLinExpr();
            expr.addTerm(-1.0, V[i * 2]); 
            expr.addTerm(1.0, V[i * 2 + 1]); 
            model.addConstr(expr, GRB.GREATER_EQUAL, 0.0, "bound" + String.valueOf(i));
        }

        // Add the PYRAMID constraint
        expr = new GRBLinExpr();
        for (int i = 0; i < num_used_attributes; i++) {
            expr.addTerm(1.0, V[i * 2 + 1]); 
        }
        model.addConstr(expr, GRB.LESS_EQUAL, 1.0, "pyramid");
        
        for (Question q : questions) {
            GRBVar indicators[] = new GRBVar[q.num_inequalities];
            double[] indicator_weights = new double[q.num_inequalities];
            // Add indicators
            for (int i = 0; i < q.num_inequalities; i++) {
                expr = new GRBLinExpr();
                for (int j = 0; j < num_used_attributes; j++) {
                    Double c = q.inequalities.get(i).get(j);
                    if (c >= 0) {
                        expr.addTerm(c, V[j * 2]);
                    } else {
                        expr.addTerm(c, V[j * 2 + 1]);
                    }
                }
                indicators[i] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "indicator" + i);
                if (!clustered) {
                    indicator_weights[i] = 1.0;
                } else {
                    indicator_weights[i] = q.inequalities.get(i).get(num_attributes);
                }
                model.addGenConstrIndicator(indicators[i], 1, expr, GRB.GREATER_EQUAL, 0.0, "constraint" + i);
            }
            // Add the constraint on the sum of the indicators
            expr = new GRBLinExpr();
            expr.addTerms(indicator_weights, indicators);
            model.addConstr(expr, GRB.EQUAL, q.num_competitors - q.k, "indicators");
        }

        apply_constraints(V);

        if (precise) {
            if (measure == 0) {
                optimize_volume(V, num_used_attributes);
            } else if (measure == 1) {
                optimize_perimeter(V, num_used_attributes);
            }
            
            int status = model.get(GRB.IntAttr.Status);
    
            if (status == 2) {
                System.out.println("OPTIMAL");
                b = getBox(model.get(GRB.DoubleAttr.ObjVal), V);
            } else if (status == 3) {
                System.out.println("INFEASIBLE");
            }  
        } else {
            Double perimeter_high = 1.0;

            b = binary_search(V, num_used_attributes, perimeter_high);
        }

        close();

        return b;
    }

    /** 
     * Solve the why-not-yet box problem with the CUBE weight constraint
     * Use num_attributes constraints on (upper bound - lower bound) for all questions, 
     * q.num_ineqaulities indicators and 1 constraint on the sum of the indicators for each question
     * The CUBE constraints are contained in the variables
     * @param topk The top-k limits for expected tuples
     * @return The CUBE result
    */
    public Box optimize_box_cube(int[] topk, boolean precise, int measure) throws GRBException {
        if (precise) {
            setup(0);
        } else {
            setup(60);
        }
        
        Box b = new Box(-1.0);

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            q.k = getTopKConstraint(topk[i], q.num_dominators);
            if (q.k < 0) {
                System.out.println("UNSATISFIABLE");
                return b;
            }
        }

        int num_used_attributes = num_attributes;

        GRBVar V[] = new GRBVar[num_used_attributes * 2];
        for (int i = 0; i < num_used_attributes; i++) {
            // The CUBE constraints are added with the variables
            V[i * 2] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "x" + String.valueOf(i) + "lower");
            V[i * 2 + 1] = model.addVar(0.0, 1.0, 0.0, GRB.CONTINUOUS, "x" + String.valueOf(i) + "upper");
        }

        GRBLinExpr expr;

        // Add constraints to make sure the upper bound is larger than or equal to the lower bound on each attribute 
        for (int i = 0; i < num_used_attributes; i++) {
            expr = new GRBLinExpr();
            expr.addTerm(-1.0, V[i * 2]); 
            expr.addTerm(1.0, V[i * 2 + 1]); 
            model.addConstr(expr, GRB.GREATER_EQUAL, 0.0, "bound" + String.valueOf(i));
        }
        
        for (Question q : questions) {
            GRBVar indicators[] = new GRBVar[q.num_inequalities];
            double[] indicator_weights = new double[q.num_inequalities];
            // Add indicators
            for (int i = 0; i < q.num_inequalities; i++) {
                expr = new GRBLinExpr();
                for (int j = 0; j < num_used_attributes; j++) {
                    Double c = q.inequalities.get(i).get(j);
                    if (c >= 0) {
                        expr.addTerm(c, V[j * 2]);
                    } else {
                        expr.addTerm(c, V[j * 2 + 1]);
                    }
                }
                indicators[i] = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "indicator" + i);
                if (!clustered) {
                    indicator_weights[i] = 1.0;
                } else {
                    indicator_weights[i] = q.inequalities.get(i).get(num_attributes);
                }
                model.addGenConstrIndicator(indicators[i], 1, expr, GRB.GREATER_EQUAL, 0.0, "constraint" + i);
            }
            // Add the constraint on the sum of the indicators
            expr = new GRBLinExpr();
            expr.addTerms(indicator_weights, indicators);
            model.addConstr(expr, GRB.EQUAL, q.num_competitors - q.k, "indicators");
        }

        apply_constraints(V);

        if (precise) {
            if (measure == 0) {
                optimize_volume(V, num_used_attributes);
            } else if (measure == 1) {
                optimize_perimeter(V, num_used_attributes);
            }
    
            int status = model.get(GRB.IntAttr.Status);
    
            if (status == 2) {
                System.out.println("OPTIMAL");
                b = getBox(model.get(GRB.DoubleAttr.ObjVal), V);
            } else if (status == 3) {
                System.out.println("INFEASIBLE");
            }  
        } else {
            Double perimeter_high = 1.0 * num_used_attributes;

            b = binary_search(V, num_used_attributes, perimeter_high);
        }
        
        close();

        return b;
    }
}