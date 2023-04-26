package wny.solver;

import java.util.ArrayList;

import com.microsoft.z3.*;
import wny.entities.Box;
import wny.entities.Constraint;
import wny.entities.Tuple;

/** 
 * A solver using the z3 library
 * It also utilizes the binary search to optimize
 * @author Zixuan Chen
*/
public class Z3Solver extends Solver {
    private Context ctx;
    private com.microsoft.z3.Solver solver;
    private RealExpr zero;
    private RealExpr one;

    /** 
     * @param tuples All tuples of a relation
     * @param expected_tuples The expected tuples in the why-not-yet question
    */
    public Z3Solver(ArrayList<Tuple> tuples, ArrayList<Tuple> expected_tuples) throws Exception {
        super(tuples, expected_tuples);
    }

    /** 
     * Set up the context and solver
     * @timeout The time limit for the model
    */
    private void setup(int timeout) {
        ctx = new Context();
        solver = ctx.mkSolver();

        zero = ctx.mkReal(0);
        one = ctx.mkReal(1);

        if (timeout != 0) {
            Params p = ctx.mkParams();
            p.add("timeout", timeout);
            solver.setParameters(p);
        }
    }
    
    /** 
     * Solve the why-not-yet satisfiability problem using indicators
     * Use 1 TRIANGLE weight constraint for all questions, q.num_ineqaulities indicators and 1 constraint on the sum of the indicators for each question
     * @param topk The top-k limits for expected tuples
     * @return The SAT result
    */
    public boolean solve_satisfiability(int[] topk) {
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

        // Add the TRIANGLE weight constraint, actually not necessary for the SAT problem but used for better presentation
        RealExpr X[] = new RealExpr[num_used_attributes];
        RealExpr sum = zero;
        for (int i = 0; i < num_used_attributes; i++) {
            X[i] = ctx.mkRealConst("x" + String.valueOf(i));
            solver.add(ctx.mkGe(X[i], zero));
            sum = (RealExpr) ctx.mkAdd(sum, X[i]);
        }
        solver.add(ctx.mkEq(sum, one));

        for (Question q : questions) {
            BoolExpr indicators[] = new BoolExpr[q.num_inequalities];
            int[] indicator_weights = new int[q.num_inequalities];
            // Add indicators
            for (int i = 0; i < q.num_inequalities; i++) {
                RealExpr left = zero;
                for (int j = 0; j < num_used_attributes; j++) {
                    left = (RealExpr) ctx.mkAdd(left, ctx.mkMul(ctx.mkReal(String.format("%.5f", q.inequalities.get(i).get(j))), X[j]));
                }
                left = (RealExpr) ctx.mkSub(left, ctx.mkReal("0.00001"));
                indicators[i] = ctx.mkLe(left, zero);
                if (!clustered) {
                    indicator_weights[i] = 1;
                } else {
                    indicator_weights[i] = q.inequalities.get(i).get(num_attributes).intValue();
                }
            }
            // Add the constraint on the sum of the indicators
            solver.add(ctx.mkPBLe(indicator_weights, indicators, q.k));
        }
        
        Status status = solver.check();
        System.out.println(status);
        
        if (status == Status.SATISFIABLE) {
            Model model = solver.getModel();
            for (int i = 0; i < num_used_attributes; i++) {
                System.out.print(String.format("%.5f", toDouble(model.evaluate(X[i], true))) + " ");
            }
            System.out.println();
            ctx.close();
            return true;
        } else {
            ctx.close();
            return false;
        }
    }

    /** 
     * Apply flexible constraints
     * @param X All variables of the problem (lower bounds and upper bounds)
    */
    private void apply_constraints(RealExpr[] X) {
        for (Constraint c : constraints) {
            if (c.type == "min") {
                solver.add(ctx.mkGe(X[c.attribute * 2], ctx.mkReal(c.value.toString())));
            } else if (c.type == "max") {
                solver.add(ctx.mkLe(X[c.attribute * 2 + 1], ctx.mkReal(c.value.toString())));
            } else if (c.type == "space") {
                solver.add(ctx.mkGe(ctx.mkSub(X[c.attribute * 2 + 1], X[c.attribute * 2]), ctx.mkReal(c.value.toString())));
            }
        }
    }

    /** 
     * Turn the output model into a box
     * @param perimeter The perimeter of this box
     * @param model The output model of the solver
     * @param X All variables of the problem (lower bounds and upper bounds)
     * @return The box
    */
    private Box getBox(Double perimeter, Model model, RealExpr[] X) {
        Box b = new Box(perimeter);
        if (model != null) {
            for (int i = 0; i < X.length; i++) {
                b.addBound(String.format("%.5f", toDouble(model.evaluate(X[i], true))));
            }
        }
        // System.out.println(perimeter);
        return b;
    }
    
    /** 
     * Utilize binary search to optimize
     * @param X All variables of the problem (lower bounds and upper bounds)
     * @param num_attributes The number of used attributes
     * @param perimeter_high The upper bound of the perimeter
     * @return The BOX result
    */
    private Box binary_search(RealExpr[] X, int num_attributes, double perimeter_high) {
        RealExpr v = zero;
        for (int i = 0; i < num_attributes; i++) {
            v = (RealExpr) ctx.mkAdd(v, ctx.mkSub(X[i * 2 + 1], X[i * 2]));
        }
        Double perimeter_low = 0.0;
        Double perimeter = perimeter_high - 0.00000001;

        Status status;
        Model model = null;

        while(true) {
            if (perimeter_high - perimeter_low < 0.01 || perimeter_high < 0.00001) {
                break;
            }
            
            System.out.print(String.format("%.5f", perimeter));
            System.out.print(" ");

            solver.push();
            solver.add(ctx.mkEq(v, ctx.mkReal(perimeter.toString())));
            status = solver.check();
            System.out.println(status);
            
            if (status == Status.SATISFIABLE) {
                model = solver.getModel();
                perimeter_low = perimeter;
            } else {
                perimeter_high = perimeter;
            }
            solver.pop();
            perimeter = (perimeter_high - perimeter_low) / 2 + perimeter_low;
        }
        
        Box b = getBox(perimeter_low, model, X);

        return b;
    }

    /** 
     * Solve the why-not-yet box problem with the TRIANGLE bound constraint
     * Use 1 TRIANGLE weight constraint, (num_attributes - 1) constarints on the lower bound to make sure every bound is non-negative and 
     * (num_attributes - 1) constraints on (upper bound - lower bound) to make sure upper bounds are larger than corresponding lower bounds for all questions, 
     * q.num_ineqaulities indicators and 1 Pseudo-Boolean constraint on the sum of the indicators for each question
     * @param topk The top-k limits for expected tuples
     * @return The BOX result
    */
    public Box optimize_box_triangle(int[] topk, boolean precise, int measure) {
        setup(60000);

        Box b = new Box(-1.0);
        
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            q.k = getTopKConstraint(topk[i], q.num_dominators);
            if (q.k < 0) {
                System.out.println("UNSATISFIABLE");
                return b;
            }
        }

        int num_used_attributes = num_attributes - 1;

        RealExpr X[] = new RealExpr[num_used_attributes * 2];
        RealExpr sum = zero;
        for (int i = 0; i < num_used_attributes; i++) {
            X[i * 2] = ctx.mkRealConst("x" + String.valueOf(i) + "lower");
            X[i * 2 + 1] = ctx.mkRealConst("x" + String.valueOf(i) + "upper");
            // Add constraints to make sure all bounds are non-negative
            solver.add(ctx.mkGe(X[i * 2], zero));
            // Add constraints to make sure the upper bound is larger than or equal to the lower bound on each attribute 
            solver.add(ctx.mkGe(X[i * 2 + 1], X[i * 2]));
            sum = (RealExpr) ctx.mkAdd(sum, X[i * 2 + 1]);
        }
        // Add the TRIANGLE constraint
        solver.add(ctx.mkLe(sum, one));

        for (Question q : questions) {
            BoolExpr indicators[] = new BoolExpr[q.num_inequalities];
            int[] indicator_weights = new int[q.num_inequalities];
            // Add indicators
            for (int i = 0; i < q.num_inequalities; i++) {
                RealExpr left = zero;
                for (int j = 0; j < num_used_attributes; j++) {
                    Double c = q.inequalities.get(i).get(j) - q.inequalities.get(i).get(num_used_attributes);
                    if (c >= 0) {
                        left = (RealExpr) ctx.mkAdd(left, ctx.mkMul(ctx.mkReal(String.format("%.5f", c)), X[j * 2]));
                    } else {
                        left = (RealExpr) ctx.mkAdd(left, ctx.mkMul(ctx.mkReal(String.format("%.5f", c)), X[j * 2 + 1]));
                    }
                }
                left = (RealExpr) ctx.mkSub(left, ctx.mkReal("0.00001"));
                indicators[i] = ctx.mkLe(left, ctx.mkMul(ctx.mkReal(String.format("%.5f", q.inequalities.get(i).get(num_used_attributes))), ctx.mkReal(-1)));
                if (!clustered) {
                    indicator_weights[i] = 1;
                } else {
                    indicator_weights[i] = q.inequalities.get(i).get(num_attributes).intValue();
                }
            }
            // Add the constraint on the sum of the indicators
            solver.add(ctx.mkPBLe(indicator_weights, indicators, q.k));
        }

        apply_constraints(X);

        double perimeter_high = 1.0;

        b = binary_search(X, num_used_attributes, perimeter_high);
        
        ctx.close();

        return b;
    }
    
    /** 
     * Solve the why-not-yet box problem with the PYRAMID bound constraint
     * Use 1 PYRAMID weight constraint, num_attributes constarints on the lower bound to make sure every bound is non-negative and 
     * num_attributes constraints on (upper bound - lower bound) to make sure upper bounds are larger than corresponding lower bounds for all questions, 
     * q.num_ineqaulities indicators and 1 Pseudo-Boolean constraint on the sum of the indicators for each question
     * @param topk The top-k limits for expected tuples
     * @return The BOX result
    */
    public Box optimize_box_pyramid(int[] topk, boolean precise, int measure) {
        setup(60000);

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

        RealExpr X[] = new RealExpr[num_used_attributes * 2];
        RealExpr sum = zero;
        for (int i = 0; i < num_used_attributes; i++) {
            X[i * 2] = ctx.mkRealConst("x" + String.valueOf(i) + "lower");
            X[i * 2 + 1] = ctx.mkRealConst("x" + String.valueOf(i) + "upper");
            // Add constraints to make sure all bounds are non-negative
            solver.add(ctx.mkGe(X[i * 2], zero));
            // Add constraints to make sure the upper bound is larger than or equal to the lower bound on each attribute
            solver.add(ctx.mkGe(X[i * 2 + 1], X[i * 2]));
            sum = (RealExpr) ctx.mkAdd(sum, X[i * 2 + 1]);
        }
        // Add the PYRAMID constraint
        solver.add(ctx.mkLe(sum, one));

        for (Question q : questions) {
            BoolExpr indicators[] = new BoolExpr[q.num_inequalities];
            int[] indicator_weights = new int[q.num_inequalities];
            // Add indicators
            for (int i = 0; i < q.num_inequalities; i++) {
                RealExpr left = zero;
                for (int j = 0; j < num_used_attributes; j++) {
                    Double c = q.inequalities.get(i).get(j);
                    if (c >= 0) {
                        left = (RealExpr) ctx.mkAdd(left, ctx.mkMul(ctx.mkReal(String.format("%.5f", c)), X[j * 2]));
                    } else {
                        left = (RealExpr) ctx.mkAdd(left, ctx.mkMul(ctx.mkReal(String.format("%.5f", c)), X[j * 2 + 1]));
                    }
                }
                left = (RealExpr) ctx.mkSub(left, ctx.mkReal("0.00001"));
                indicators[i] = ctx.mkLe(left, zero);
                if (!clustered) {
                    indicator_weights[i] = 1;
                } else {
                    indicator_weights[i] = q.inequalities.get(i).get(num_attributes).intValue();
                }
            }
            // Add the constraint on the sum of the indicators
            solver.add(ctx.mkPBLe(indicator_weights, indicators, q.k));
        }

        apply_constraints(X);

        double perimeter_high = 1.0;

        b = binary_search(X, num_used_attributes, perimeter_high);
        
        ctx.close();

        return b;
    }

    /** 
     * Solve the why-not-yet box problem with the CUBE bound constraint
     * Use num_attributes constarints on the lower bound to make sure every bound is non-negative, 
     * num_attributes constraints on (upper bound - lower bound) to make sure upper bounds are larger than corresponding lower bounds,
     * and num_attributes constraints on the upper bound to implement CUBE constraints for all questions, 
     * q.num_ineqaulities indicators and 1 Pseudo-Boolean constraint on the sum of the indicators for each question
     * @param topk The top-k limits for expected tuples
     * @return The BOX result
    */
    public Box optimize_box_cube(int[] topk, boolean precise, int measure) {
        setup(60000);

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

        RealExpr X[] = new RealExpr[num_used_attributes * 2];
        for (int i = 0; i < num_used_attributes; i++) {
            X[i * 2] = ctx.mkRealConst("x" + String.valueOf(i) + "lower");
            X[i * 2 + 1] = ctx.mkRealConst("x" + String.valueOf(i) + "upper");
            // Add constraints to make sure all bounds are non-negative
            solver.add(ctx.mkGe(X[i * 2], zero));
            // Add constraints to make sure the upper bound is larger than or equal to the lower bound on each attribute
            solver.add(ctx.mkGe(X[i * 2 + 1], X[i * 2]));
            // Add CUBE constraints
            solver.add(ctx.mkLe(X[i * 2 + 1], one));
        }

        for (Question q : questions) {
            BoolExpr indicators[] = new BoolExpr[q.num_inequalities];
            int[] indicator_weights = new int[q.num_inequalities];
            // Add indicators
            for (int i = 0; i < q.num_inequalities; i++) {
                RealExpr left = zero;
                for (int j = 0; j < num_used_attributes; j++) {
                    Double c = q.inequalities.get(i).get(j);
                    if (c >= 0) {
                        left = (RealExpr) ctx.mkAdd(left, ctx.mkMul(ctx.mkReal(String.format("%.5f", c)), X[j * 2]));
                    } else {
                        left = (RealExpr) ctx.mkAdd(left, ctx.mkMul(ctx.mkReal(String.format("%.5f", c)), X[j * 2 + 1]));
                    }
                }
                left = (RealExpr) ctx.mkSub(left, ctx.mkReal("0.00001"));
                indicators[i] = ctx.mkLe(left, zero);
                if (!clustered) {
                    indicator_weights[i] = 1;
                } else {
                    indicator_weights[i] = q.inequalities.get(i).get(num_attributes).intValue();
                }
            }
            // Add the constraint on the sum of the indicators
            solver.add(ctx.mkPBLe(indicator_weights, indicators, q.k));
        }   

        apply_constraints(X);
      
        double perimeter_high = 1.0 * num_used_attributes;

        b = binary_search(X, num_used_attributes, perimeter_high);
        
        ctx.close();
        
        return b;
    }

    /** 
     * Solve the why-not-yet box problem with the CUBE bound constraint using the direct encoding
     * Use num_attributes constarints on the lower bound to make sure every bound is non-negative, 
     * num_attributes constraints on (upper bound - lower bound) to make sure upper bounds are larger than corresponding lower bounds,
     * and num_attributes constraints on the upper bound to implement CUBE constraints for all questions, 
     * q.num_ineqaulities indicators and 1 Forall quantifier on the sum of the indicators for each question
     * 
     * @param topk The top-k limits for expected tuples
     * @return The BOX result
    */
    public Box optimize_box_cube_forall(int[] topk, boolean precise) {
        setup(60000);

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

        RealExpr X[] = new RealExpr[num_used_attributes * 2];
        RealExpr Y[] = new RealExpr[num_used_attributes];
        BoolExpr YinX = ctx.mkBool(true);

        for (int i = 0; i < num_used_attributes; i++) {
            X[i * 2] = ctx.mkRealConst("x" + String.valueOf(i) + "lower");
            X[i * 2 + 1] = ctx.mkRealConst("x" + String.valueOf(i) + "upper");
            Y[i] = ctx.mkRealConst("x" + String.valueOf(i));
            // Add constraints to make sure all bounds are non-negative
            solver.add(ctx.mkGe(X[i * 2], zero));
            // Add constraints to make sure the upper bound is larger than or equal to the lower bound on each attribute
            solver.add(ctx.mkGe(X[i * 2 + 1], X[i * 2]));
            // Add CUBE constraints
            solver.add(ctx.mkLe(X[i * 2 + 1], one));
            YinX = ctx.mkAnd(YinX, ctx.mkGe(Y[i], X[i * 2]), ctx.mkLe(Y[i], X[i * 2 + 1])); // l <= w <= h
        }

        for (Question q : questions) {
            BoolExpr indicators[] = new BoolExpr[q.num_inequalities];
            int[] indicator_weights = new int[q.num_inequalities];
            // Add indicators
            for (int i = 0; i < q.num_inequalities; i++) {
                RealExpr left = zero;
                for (int j = 0; j < num_used_attributes; j++) {
                    Double c = q.inequalities.get(i).get(j);
                    left = (RealExpr) ctx.mkAdd(left, ctx.mkMul(ctx.mkReal(String.format("%.5f", c)), Y[j]));
                }
                left = (RealExpr) ctx.mkSub(left, ctx.mkReal("0.00001"));
                indicators[i] = ctx.mkLe(left, zero);
                if (!clustered) {
                    indicator_weights[i] = 1;
                } else {
                    indicator_weights[i] = q.inequalities.get(i).get(num_attributes).intValue();
                }
            }
            // Add the quantifier on the sum of the indicators
            solver.add(ctx.mkForall(Y, ctx.mkImplies(YinX, ctx.mkPBLe(indicator_weights, indicators, q.k)), 0, null, null, null, null));
        }   

        apply_constraints(X);
      
        double perimeter_high = 1.0 * num_used_attributes;

        b = binary_search(X, num_used_attributes, perimeter_high);
        
        ctx.close();
        
        return b;
    }
}