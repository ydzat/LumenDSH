package local.sliverwolf.assistant;

import de.bwaldvogel.liblinear.*;
import libsvm.*;

/** Small on-device experiment: fixed hyperparameters, frozen CAM++ embeddings. */
final class OwnerBackend {
    interface Predictor {boolean accepts(float[] vector);}
    static final class Result {
        final Predictor predictor;final long fitNanos;
        Result(Predictor predictor,long fitNanos){this.predictor=predictor;this.fitNanos=fitNanos;}
    }
    static double[] normalized(float[] values) {
        double norm=0;for(float v:values){if(!Float.isFinite(v))throw new IllegalArgumentException("Non-finite embedding");norm+=(double)v*v;}
        if(norm<1e-12)throw new IllegalArgumentException("Empty embedding");
        double[] result=new double[values.length];for(int i=0;i<values.length;i++)result[i]=values[i]/Math.sqrt(norm);return result;
    }
    private static Feature[] linear(float[] vector) {
        double[] values=normalized(vector);Feature[] nodes=new Feature[values.length+1];
        for(int i=0;i<values.length;i++)nodes[i]=new FeatureNode(i+1,values[i]);
        nodes[values.length]=new FeatureNode(values.length+1,1);return nodes;
    }
    private static svm_node[] kernel(float[] vector) {
        double[] values=normalized(vector);svm_node[] nodes=new svm_node[values.length];
        for(int i=0;i<values.length;i++){nodes[i]=new svm_node();nodes[i].index=i+1;nodes[i].value=values[i];}return nodes;
    }
    static Result fit(boolean rbf,float[][] positive,float[][] negative) {
        if(positive.length<1||negative.length<1)throw new IllegalArgumentException("Both classes are required");
        int dimension=positive[0].length;
        for(float[][] group:new float[][][]{positive,negative})for(float[] v:group)if(v.length!=dimension)throw new IllegalArgumentException("Embedding dimension mismatch");
        long start=System.nanoTime();int n=positive.length+negative.length;
        if(!rbf) {
            Linear.disableDebugOutput();Problem problem=new Problem();problem.l=n;problem.n=dimension+1;problem.bias=1;
            problem.x=new Feature[n][];problem.y=new double[n];
            for(int i=0;i<n;i++){problem.x[i]=linear(i<positive.length?positive[i]:negative[i-positive.length]);problem.y[i]=i<positive.length?1:-1;}
            Parameter parameters=new Parameter(SolverType.L2R_LR,1,300,.001);
            parameters.setWeights(new double[]{n/(2.0*positive.length),n/(2.0*negative.length)},new int[]{1,-1});
            Model model=Linear.train(problem,parameters);
            return new Result(v->Linear.predict(model,linear(v))==1,System.nanoTime()-start);
        }
        svm.svm_set_print_string_function(s->{});svm_problem problem=new svm_problem();problem.l=n;problem.x=new svm_node[n][];problem.y=new double[n];
        for(int i=0;i<n;i++){problem.x[i]=kernel(i<positive.length?positive[i]:negative[i-positive.length]);problem.y[i]=i<positive.length?1:-1;}
        svm_parameter parameters=new svm_parameter();parameters.svm_type=svm_parameter.C_SVC;parameters.kernel_type=svm_parameter.RBF;
        parameters.C=10;parameters.gamma=1;parameters.cache_size=8;parameters.eps=.001;parameters.shrinking=1;parameters.probability=0;
        parameters.nr_weight=2;parameters.weight_label=new int[]{1,-1};parameters.weight=new double[]{n/(2.0*positive.length),n/(2.0*negative.length)};
        String error=svm.svm_check_parameter(problem,parameters);if(error!=null)throw new IllegalArgumentException(error);
        svm_model model=svm.svm_train(problem,parameters);
        return new Result(v->svm.svm_predict(model,kernel(v))==1,System.nanoTime()-start);
    }
    static final class Adapted {
        final Predictor predictor;final String name;final int crossValidated;final long elapsedNanos;
        Adapted(Predictor predictor,String name,int crossValidated,long elapsedNanos){this.predictor=predictor;this.name=name;this.crossValidated=crossValidated;this.elapsedNanos=elapsedNanos;}
    }
    static Adapted adapt(float[][] owner,float[][] background) {
        if(owner.length<3||background.length!=3)throw new IllegalArgumentException("Incomplete adaptation samples");
        long start=System.nanoTime();float[][] negatives={background[0],background[2]};
        boolean selected=false;int best=-1;
        for(boolean rbf:new boolean[]{false,true}) {
            int score=0;
            for(int held=0;held<owner.length;held++) {
                float[][] train=new float[owner.length-1][];int cursor=0;
                for(int i=0;i<owner.length;i++)if(i!=held)train[cursor++]=owner[i];
                Result candidate=fit(rbf,train,negatives);
                if(candidate.predictor.accepts(owner[held]))score++;
                if(!candidate.predictor.accepts(background[1]))score++;
            }
            if(score>best){best=score;selected=rbf;}
        }
        // Cross-validation selects the backend; fit the selected model on all saved samples.
        Result result=fit(selected,owner,negatives);
        return new Adapted(result.predictor,selected?"rbf_svm":"logistic",best,System.nanoTime()-start);
    }
    static String compare(float[][] training,float[][] negatives,float[][] heldOut,float[][] negativeHeldOut) {
        StringBuilder report=new StringBuilder();
        for(boolean rbf:new boolean[]{false,true}) {
            Result fit=fit(rbf,training,negatives);int accepted=0,rejected=0;
            for(float[] v:heldOut)if(fit.predictor.accepts(v))accepted++;
            for(float[] v:negativeHeldOut)if(!fit.predictor.accepts(v))rejected++;
            report.append(rbf?"RBF-SVM":"Logistic regression").append(": owner ").append(accepted).append("/").append(heldOut.length)
                .append(", background rejected ").append(rejected).append("/").append(negativeHeldOut.length)
                .append(", fit ").append(fit.fitNanos/1000000.0).append(" ms\n");
        }
        return report.toString();
    }
}
