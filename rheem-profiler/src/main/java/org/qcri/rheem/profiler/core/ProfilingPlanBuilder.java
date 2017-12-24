package org.qcri.rheem.profiler.core;

import org.qcri.rheem.basic.data.Tuple2;
import org.qcri.rheem.core.api.Configuration;
import org.qcri.rheem.core.function.FlatMapDescriptor;
import org.qcri.rheem.core.function.PredicateDescriptor;
import org.qcri.rheem.core.function.ReduceDescriptor;
import org.qcri.rheem.core.function.TransformationDescriptor;
import org.qcri.rheem.core.plan.rheemplan.ExecutionOperator;
import org.qcri.rheem.core.plan.rheemplan.InputSlot;
import org.qcri.rheem.core.plan.rheemplan.OutputSlot;
import org.qcri.rheem.core.types.DataSetType;
import org.qcri.rheem.flink.operators.*;
import org.qcri.rheem.profiler.core.api.*;
import org.qcri.rheem.profiler.data.DataGenerators;
import org.qcri.rheem.profiler.data.UdfGenerators;
import org.qcri.rheem.profiler.java.JavaOperatorProfilers;
import org.qcri.rheem.profiler.spark.SparkOperatorProfilers;
import org.qcri.rheem.profiler.spark.SparkOperatorProfiler;
import org.qcri.rheem.profiler.spark.SparkPlanOperatorProfilers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.function.Supplier;

import static org.qcri.rheem.profiler.java.JavaOperatorProfilers.createJavaReduceByProfiler;

/**
 * Generates rheem plans for profiling.
 */
public class ProfilingPlanBuilder implements Serializable {

    /**
     * Logging for profiling plan building
     */
    private static final Logger logger =  LoggerFactory.getLogger(ProfilingRunner.class);


    /**
     * Configuration file for the plan builder
     */
    private static ProfilingConfig profilingConfig;

    /**
     * Used to stop loop body connection from going into an endless loop
     *
     */
    private static LoopTopology currentLoop;

    public static List< ? extends OperatorProfiler> PlanBuilder(Topology topology, ProfilingConfig profilingConfig){
        //topology = topology;
        //profilingConfig = profilingConfig;

        // check the topology node number
        if (topology.getNodeNumber()==1){
            return singleOperatorProfilingPlanBuilder(profilingConfig);
        } else {
            return singleOperatorProfilingPlanBuilder(profilingConfig);
        }
    }

    public static List<List<PlanProfiler>> exhaustiveProfilingPlanBuilder(List<Shape> shapes, ProfilingConfig profilingConfiguration){
        profilingConfig = profilingConfiguration;
        assert (shapes.size()!=0);

        List<List<PlanProfiler>> topologyPlanProfilers = new ArrayList<>();

        for(Shape s:shapes){
            if (profilingConfig.getProfilingPlanGenerationEnumeration().equals("exhaustive")){
                topologyPlanProfilers.add(randomProfilingPlanBuilder(s,1, shapes));
            } else {
                // TODO: add more profiling plan generation enumeration: random, worstPlanGen (more execution time),betterPlanGen (less execution time)
                topologyPlanProfilers.add(randomProfilingPlanBuilder(s,1, shapes));
            }
        }
        return topologyPlanProfilers;
    }


    /**
     * Generates a list that contains a {@param numberPlans} of random {@link PlanProfiler}s for the given {@param shape}
     * @param shape
     * @return
     */
    public static List<PlanProfiler> randomProfilingPlanBuilder(Shape shape, int numberPlans, List<Shape> shapes){
        List<PlanProfiler> planProfilers = new ArrayList<>();
        PlanProfiler planProfiler = new PlanProfiler(shape, profilingConfig);
        List<Shape> tmpSubShapes = new ArrayList<>();
        // Loop through all dataTypes
        for(DataSetType type:profilingConfig.getDataType()){
            // Loop through all plateforms
            for (String platform:profilingConfig.getProfilingPlateform()){
                // Set the shape's platform
                shape.setPlateform(platform);
                //Loop through all unary operators
                // TODO: we have to multiply by number of pipelines existing in current shape and number of nodes per pipeline
                for (String unaryOperator:profilingConfig.getUnaryExecutionOperators()){
                    //Loop through all binary operators
                    for (String binaryOperator:profilingConfig.getBinaryExecutionOperators()){
                        //Loop through all loop operators
                        for (String loopOperator:profilingConfig.getLoopExecutionOperators()) {
                            // Fill the sources
                            for (Topology t : shape.getSourceTopologies()) {
                                //if (t.getNodes().isEmpty())
                                prepareSource(t, type, platform);
                            }

                            // TODO: Fill pipeline topologies with exhaustive way (pass a list of operators (e.g:[map,map,map])   to be filled in the below loop)
                            // Fill with unary operator profilers
                            for (Topology t : shape.getPipelineTopologies()) {
                                // check if the nodes are not already filled in the source or sink
                                if ((t.getNodes().isEmpty() || (!t.isSource())))
                                    for (int i = 1; i <= t.getNodeNumber(); i++)
                                        // TODO: fill the nodes with the operator list
                                        t.getNodes().push(randomUnaryNodeFill(type, platform));
                            }

                            // Fill with binary operator profilers
                            for (Topology t : shape.getJunctureTopologies()) {
                                // check if the nodes are not already filled in the source or sink
                                //if (t.getNodes().isEmpty())
                                t.getNodes().push(binaryNodeFill(type, platform));
                            }

                            // Fill the loop topologies
                            for (Topology t : shape.getLoopTopologies()) {
                                t.getNodes().push(loopNodeFill(type, platform));
                            }

                            // Fill the sinks
                            //for(Topology t:shape.getSinkTopology()){
                            //if (shape.getSinkTopology().getNodes().isEmpty())
                            prepareSink(shape.getSinkTopology(), type, platform);
                            //}

                            // Build the planProfiler
                            buildPlanProfiler(shape, type);

                            // add the filled shape to tempSubShapes
                            tmpSubShapes.add(shape.clone());

                            // Reset shape
                            shape.resetAllNodes();
                            //planProfiler
                            planProfilers.add(planProfiler);
                        }
                    }
                }
            }
        }
        shape.setSubShapes(tmpSubShapes);
        return planProfilers;
    }

    /**
     * Generates a list of exhaustive {@link PlanProfiler}s for the given {@link Shape}
     * @param shape
     * @return
     */
    public static List<PlanProfiler> exhaustiveProfilingPlanBuilder(Shape shape){
        List<PlanProfiler> planProfilers = new ArrayList<>();
        PlanProfiler planProfiler = new PlanProfiler(shape, profilingConfig);

        // Start with the sink operator
        //shape.getSinkTopology().getNode
        return planProfilers;
    }
        /**
         * Build the plan profiler; connect the nodes and build rheem plan;
         */
    private static void buildPlanProfiler(Shape shape, DataSetType type){

        // Start with the sink operator
        Topology sinkTopology = shape.getSinkTopology();


        // Check the dataType
        checkDataType(shape, type);
        // recursively connect predecessor nodes

        Stack<Tuple2<String, OperatorProfiler>> sinkNodes = (Stack<Tuple2<String, OperatorProfiler>>) sinkTopology.getNodes().clone();

        //Tuple2<String, OperatorProfiler> sinkNode =sinkTopology.getNodes().pop();
        // Check if it's not already connected
        Tuple2<String, OperatorProfiler> lastConnectedNode = connectNodes(sinkTopology, sinkTopology.getNodes().pop(),0);


        sinkTopology.setNodes(sinkNodes);
        // connect the lastConnectedNode with the head node of successive topology
        //for

        //preparePlanProfiler(planProfiler);

    }

    private static void checkDataType(Shape shape, DataSetType type) {
        final DataSetType tst = DataSetType.createDefault(Integer.class);
        for (Topology t:shape.getAllTopologies()){
            t.getNodes().stream()
                    .forEach(n->{
                        OperatorProfiler op = n.getField1();
                        DataSetType opIn1DataType,opOutDataType;
                        // check the operator type
                        // handle the source case
                        if (op.getOperator().isSource()) {
                            opIn1DataType = null;
                            opOutDataType = op.getOperator().getOutput(0).getType();
                        }else if (op.getOperator().isSink()) {
                            opIn1DataType = op.getOperator().getInput(0).getType();
                            opOutDataType = null;
                        }else {
                            opIn1DataType = op.getOperator().getInput(0).getType();
                            opOutDataType = op.getOperator().getOutput(0).getType();
                        }

                        // check if the operator profiler has the required dataType
                        if((opIn1DataType!=null)&&(!opIn1DataType.equals(profilingConfig.getDataType()))){
                            // TODO: Need to change the operator udf to be compatible with new datatype or create intermediate operator
                            // Remplace the slot
                            op.getOperator().setInput(0,new InputSlot<>("in", op.getOperator(), type));
                        }

                        if((opOutDataType!=null)&&(!opOutDataType.equals(profilingConfig.getDataType()))){
                            // TODO: Need to change the operator udf to be compatible with new datatype or create intermediate operator
                            // Remplace the slot
                            op.getOperator().setOutput(0,new OutputSlot<>("out", op.getOperator(), type));
                        }
                    });

            if(profilingConfig.getDataType().equals(DataSetType.createDefault(Integer.class))){
                // Check that all dataTypes are conforming to the datatype otherwise change them accordingly

            } else if(profilingConfig.getDataType().equals(DataSetType.createDefault(String.class))){

            } else if(profilingConfig.getDataType().equals(DataSetType.createDefault(List.class))){

            }
        }
    }

    /**
     * The below method will connect nodes inside a topology and returns the head node so to connect it with the previous topology
     * @param topology
     * @return
     */
    private static Tuple2<String,OperatorProfiler> connectNodes(Topology topology, Tuple2<String, OperatorProfiler> currentnode, int inputSlot) {

        // check if the current topology is loop
        if (topology.isLoop())
            return connectLoopNodes((LoopTopology) topology, currentnode,inputSlot);
        Stack nodes = (Stack) topology.getNodes().clone();

        // previous = bottom to top ((previous) sink => (current) source)
        Tuple2<String, OperatorProfiler> previousNode = null;
        Tuple2<String, OperatorProfiler> currentNode = currentnode;

        // Loop through all the nodes
        while(!nodes.isEmpty()){
            previousNode = currentNode;
            currentNode = (Tuple2<String, OperatorProfiler>) nodes.pop();


                // TODO: check the dataType and correct it
                // connect previous with current node
                try{
                    currentNode.getField1().getOperator().connectTo(0,previousNode.getField1().getOperator(),inputSlot);
                } catch (Exception e){
                    logger.error(e.getMessage());
                }

                // Reset inputSlot to 0 after connecting the last pipeline node with the jucture topology's node
                // so to avoid error when connecting the nodes of a pipeline topology
                if (inputSlot!=0)
                    inputSlot = 0;
        }

        // at the end of this method should connect the lastConnectedNode with the head node of predecessor topology
        // recurse the predecessor tpgs
        //get the predecessors of tmp topology
        if  (!(topology.getInput(0).getOccupant()==null)){
            List<Topology> predecessors = topology.getPredecessors();
            int inputslot = 0;
            for(Topology t:predecessors){
                connectNodes(t, currentNode, inputslot);
                inputslot++;
            }

        }

        return currentNode;
    }

    /**
     * Connects a loop topology what's inside a loop then returns the source node (previous node to a loop head )
     * @param loopTopology
     * @param currentnode
     * @param inputSlot
     * @return
     */
    private static Tuple2<String,OperatorProfiler> connectLoopNodes(LoopTopology loopTopology, Tuple2<String, OperatorProfiler> currentnode, int inputSlot) {

        Stack nodes = (Stack) loopTopology.getNodes().clone();


        if (loopTopology.equals(currentLoop)){
            // should stop the current loop execution adn return the initialization input

            // thereis connect here Loop => initial iteration on the current node
            Tuple2<String, OperatorProfiler> loopNode = currentnode;
            loopNode = (Tuple2<String, OperatorProfiler>) nodes.pop();

            loopNode.getField1().getOperator().connectTo(LoopTopology.ITERATION_OUTPUT_INDEX,
                    currentnode.getField1().getOperator(),inputSlot);

            // return null
            return new Tuple2<>();
        }

        currentLoop = loopTopology;
        Tuple2<String, OperatorProfiler> previousNode = null;
        Tuple2<String, OperatorProfiler> loopNode = currentnode;

        if (loopNode.getField1().getOperator().isSink()){
            // Handle the case where loop topology is sink topology too

            // update the nodes
            // previous = bottom to top ((previous) sink => (current) source)
            previousNode=loopNode;
            loopNode = (Tuple2<String, OperatorProfiler>) nodes.pop();

            // connect the loop topology to final output
            loopNode.getField1().getOperator().connectTo(LoopTopology.FINAL_OUTPUT_INDEX,previousNode.getField1().getOperator(),inputSlot);
            // Get the start Iteration Topology
            Topology startInterationTopology = loopTopology.getLoopBodyInput();

            // Get the final Iteration Topology
            Topology endIterationTopology = loopTopology.getLoopBodyOutput();

            // connect final Iteration Topology to loop topology
            connectNodes(endIterationTopology, loopNode,LoopTopology.ITERATION_INPUT_INDEX);
            // Reset current loop

        }
        // TODO: Handle the case where the loop is not a sink topology

        //return connectNodes(loopTopology,loopNode,inputSlot);

        //get the initial topology of loop topology
        if  (!(loopTopology.getInput(0).getOccupant()==null)){
            //If exist
            List<Topology> predecessors = loopTopology.getPredecessors();

            // this will connect the INITIAL_INPUT_INDEX
            //connectNodes(predecessors.get(0), loopNode, LoopTopology.INITIAL_INPUT_INDEX);
            Tuple2<String, OperatorProfiler> initialInputIndexNode = predecessors.get(0).getNodes().peek();
            //initialInputIndexNode.getField1().getOperator().connectTo(0,loopNode.getField1().getOperator(),LoopTopology.INITIAL_INPUT_INDEX);

            // connect the nodes of the initialization topology
            connectNodes(predecessors.get(0), loopNode,LoopTopology.INITIAL_INPUT_INDEX);
        } else{
            // means the loop is a source node too
            // connect the source node to loop node
            Tuple2<String, OperatorProfiler> sourceNode;
            sourceNode = (Tuple2<String, OperatorProfiler>) nodes.pop();

            sourceNode.getField1().getOperator().connectTo(0,loopNode.getField1().getOperator(), LoopTopology.INITIAL_INPUT_INDEX);
        }

        currentLoop = new LoopTopology(0,0);

        // should

        return loopNode;
    }

    private static void preparePlanProfiler(Topology topology) {
        //for(Topology t:planProfiler.ge)
    }

    /**
     * Add nodes to source Topology (i.e. pipeline topology)
     * PS: Source node i counted in the node number
     * @param topology
     */
    private static void prepareSource(Topology topology, DataSetType type, String plateform) {
        // add the first source node
        topology.getNodes().push(sourceNodeFill(type, plateform));

        // exit in the case of a loop
        if (topology.isLoop())
                return;

        // add the remaining nodes with unary nodes
        for(int i=1;i<=topology.getNodeNumber();i++)
                topology.getNodes().push(randomUnaryNodeFill(type, plateform));
    }

    /**
     * Add the sink to the sink topology; NOTE: that the sink is node included as a node
     * @param topology
     */
    private static void prepareSink(Topology topology, DataSetType type, String plateform) {

        // check if the sink is already filled as pipeline/source Topology.
        if (topology.getNodes().empty()){
            //Add the first unary nodes
            for(int i=1;i<=topology.getNodeNumber();i++)
                topology.getNodes().push(randomUnaryNodeFill(type, plateform));
            // Add the last sink node
            topology.getNodes().push(sinkNodeFill(type, plateform));
        } else{
            topology.getNodes().push( sinkNodeFill(type, plateform));
        }

    }

    private static void prepareSinkLoop(LoopTopology topology) {
    }

    /**
     * Fills the toplogy instance with unary profiling operators
     * @return
     */
    private static Tuple2<String,OperatorProfiler> sinkNodeFill(DataSetType type, String plateform){
        // we currently support use the collection source
        String operator = profilingConfig.getSinkExecutionOperators().get(0);
        Tuple2<String,OperatorProfiler> returnNode =new Tuple2(operator, getProfilingOperator(operator, type, plateform,1,1)) ;
        returnNode.getField1().setUDFcomplexity(1);
        return returnNode;
    }

    /**
     * Fills the toplogy instance with unary profiling operators
     * @return
     */
    private static Tuple2<String,OperatorProfiler> sourceNodeFill(DataSetType type, String plateform){
        // we currently support collection source
        String operator = profilingConfig.getSourceExecutionOperators().get(1);
        Tuple2<String,OperatorProfiler> returnNode = new Tuple2(operator, getProfilingOperator(operator, type, plateform,1,1));
        returnNode.getField1().setUDFcomplexity(1);
        return returnNode;
    }

    /**
     * Fills the toplogy instance with unary profiling operators
     * @return
     */
    private static Tuple2<String,OperatorProfiler> randomUnaryNodeFill(DataSetType type, String plateform){
        int rnd = (int)(Math.random() * profilingConfig.getUnaryExecutionOperators().size());
        int udfRnd = 1 + (int)(Math.random() * profilingConfig.getUdfsComplexity().size());
        String operator = profilingConfig.getUnaryExecutionOperators().get(rnd);
        Tuple2<String,OperatorProfiler> returnNode = new Tuple2(operator, getProfilingOperator(operator, type, plateform,1,udfRnd));
        returnNode.getField1().setUDFcomplexity(udfRnd);
        return returnNode;
    }

    /**
     * Fills the toplogy instance with unary profiling operators
     * @return
     */
    private static Tuple2<String,OperatorProfiler> UnaryNodeFill(DataSetType type, String plateform, String operator){
        int udfRnd = 1 + (int)(Math.random() * profilingConfig.getUdfsComplexity().size());
        Tuple2<String,OperatorProfiler> returnNode = new Tuple2(operator, getProfilingOperator(operator, type, plateform,1,udfRnd));
        returnNode.getField1().setUDFcomplexity(udfRnd);
        return returnNode;
    }

    /**
     * Fills the toopology instance with binary profiling operator
     * @return
     */
    private static Tuple2<String,OperatorProfiler> binaryNodeFill(DataSetType type, String plateform){
        int rnd = (int)(Math.random() * profilingConfig.getBinaryExecutionOperators().size());
        int udfRnd = 1 + (int)(Math.random() * profilingConfig.getUdfsComplexity().size());
        String operator = profilingConfig.getBinaryExecutionOperators().get(rnd);
        return new Tuple2(operator, getProfilingOperator(operator, type, plateform,1,udfRnd));
    }

    /**
     * Fills the toopology instance with binary profiling operator
     * @return
     */
    private static Tuple2<String,OperatorProfiler> binaryNodeFill(DataSetType type, String plateform, String operator){
        int udfRnd = 1 + (int)(Math.random() * profilingConfig.getUdfsComplexity().size());
        Tuple2<String,OperatorProfiler> returnNode = new Tuple2(operator, getProfilingOperator(operator, type, plateform,1,udfRnd));
        returnNode.getField1().setUDFcomplexity(udfRnd);
        return returnNode;
    }

    /**
     * Fills the toopology instance with binary profiling operator
     * @return
     */
    private static Tuple2<String,OperatorProfiler> loopNodeFill(DataSetType type, String plateform){
        int rnd = (int)(Math.random() * profilingConfig.getLoopExecutionOperators().size());
        int udfRnd = 1 + (int)(Math.random() * profilingConfig.getUdfsComplexity().size());
        String operator = profilingConfig.getLoopExecutionOperators().get(rnd);
        return new Tuple2(operator, getProfilingOperator(operator, type, plateform,1,udfRnd));
    }

    /**
     * Builds all possible combinations of profiling plans of the input {@link Topology}ies
     * @param topologies
     * @param profilingConfiguration
     * @return List of all sink profiling plans
     */

    public static List<List<OperatorProfiler>> exhaustiveProfilingTopologyPlanBuilder(List<Topology> topologies,ProfilingConfig profilingConfiguration){
        profilingConfig = profilingConfiguration;
        assert (topologies.size()!=0);

        List<List<OperatorProfiler>> topologyPlanProfilers = new ArrayList<>();

        for(Topology t:topologies){
            if (profilingConfig.getProfilingPlanGenerationEnumeration().equals("exhaustive")){
                topologyPlanProfilers.add(singleExhaustiveProfilingPlanBuilder2(t));
            } else {
                // TODO: add more profiling plan generation enumeration: random, worstPlanGen (more execution time),betterPlanGen (less execution time)
                topologyPlanProfilers.add(singleExhaustiveProfilingPlanBuilder2(t));
            }
        }
        return topologyPlanProfilers;
    }



    public static List<OperatorProfiler> singleExhaustiveProfilingPlanBuilder2(Topology topology){
        List<OperatorProfiler> planProfilers = null;

        return planProfilers;
    }





    /**
     * Builds a profiling plan for a pipeline Topology
     * @param shape
     * @param profilingConfiguration
     * @return
     */
    public static List<PlanProfiler> pipelineProfilingPlanBuilder(Shape shape,ProfilingConfig profilingConfiguration){
        profilingConfig = profilingConfiguration;
        if (profilingConfig.getProfilingPlanGenerationEnumeration().equals("exhaustive")){
            return exhaustivePipelineProfilingPlanBuilder(shape);
        } else {
            // TODO: add more profiling plan generation enumeration: random, worstPlanGen (more execution time),betterPlanGen (less execution time)
            return exhaustivePipelineProfilingPlanBuilder(shape);
        }
    }

    /**
     * To remove
     * @param shape
     * @param profilingConfiguration
     * @return
     */
    public static List<PlanProfiler> exhaustiveProfilingPlanBuilder(Shape shape,ProfilingConfig profilingConfiguration){
        profilingConfig = profilingConfiguration;
        if (profilingConfig.getProfilingPlanGenerationEnumeration().equals("exhaustive")){
            return exhaustivePipelineProfilingPlanBuilder(shape);
        } else {
            // TODO: add more profiling plan generation enumeration: random, worstPlanGen (more execution time),betterPlanGen (less execution time)
            return exhaustivePipelineProfilingPlanBuilder(shape);
        }
    }

    /**
     * Still for test
     * @param shape
     * @return
     */

    private static List<PlanProfiler> exhaustivePipelineProfilingPlanBuilder(Shape shape) {
        List<PlanProfiler> profilingPlans = new ArrayList<>();

        PlanProfiler planProfiler = new PlanProfiler(shape, profilingConfig);
        // Set the pla Profiler

            // Set source operator profiler
            planProfiler.setSourceOperatorProfiler(SparkOperatorProfilers.createSparkCollectionSourceProfiler(1,String.class));
            // Set unary operator profiler
            planProfiler.setUnaryOperatorProfilers(Arrays.asList(SparkOperatorProfilers.createSparkMapProfiler(1,3)));
            // Set sink operator profiler
            planProfiler.setSinkOperatorProfiler(SparkOperatorProfilers.createSparkLocalCallbackSinkProfiler(1));

        profilingPlans.add(planProfiler);
        return profilingPlans;
    }

    /**
     * Builds an operator profiling specifically for single operator profiling with fake jobs
     * @param profilingConfig
     * @return
     */
    public static List<? extends OperatorProfiler> singleOperatorProfilingPlanBuilder(ProfilingConfig profilingConfig){
        if (profilingConfig.getProfilingPlanGenerationEnumeration().equals("exhaustive")){
            return exhaustiveSingleOperatorProfilingPlanBuilder(profilingConfig.getProfilingPlateform().get(0));
        } else {
            // TODO: add more profiling plan generation enumeration: random, worstPlanGen (more execution time),betterPlanGen (less execution time)
            return exhaustiveSingleOperatorProfilingPlanBuilder(profilingConfig.getProfilingPlateform().get(0));
        }
    }

    /**
     * Get the {@link OperatorProfiler} of the input string
     * @param operator
     * @return
     */
    private static OperatorProfiler getProfilingOperator(String operator, DataSetType type, String plateform,
                                                         int dataQuantaScale, int UdfComplexity) {
        //List allCardinalities = this.profilingConfig.getInputCardinality();
        //List dataQuata = this.profilingConfig.getDataQuantaSize();
        //List UdfComplexity = this.profilingConfig.getUdfsComplexity();
        String fileUrl=new Configuration().getStringProperty("rheem.core.log.syntheticData");
        switch (plateform) {
            case "flink":
                switch (operator) {
                    case "textsource":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkTextFileSource op= new FlinkTextFileSource("file:///"+fileUrl); op.setName("FinkTextFileSource"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                                );
                        //return SparkPlanOperatorProfilers.createSparkTextFileSourceProfiler(1, type);
                    case "collectionsource":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkCollectionSource op= new FlinkCollectionSource(type); op.setName("FinkCollectionFileSource"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                    case "map":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkMapOperator op= new FlinkMapOperator(
                                        type,
                                        type,
                                        new TransformationDescriptor<>(
                                            DataGenerators.generateUDF(UdfComplexity,dataQuantaScale,type,"map"),
                                            type.getDataUnitType().getTypeClass(),
                                            type.getDataUnitType().getTypeClass()
                                        )
                                    );
                                    op.setName("FinkMap"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return SparkPlanOperatorProfilers.createSparkMapProfiler(1, UdfComplexity,type);

                    case "filter":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkFilterOperator op= new FlinkFilterOperator(type, new PredicateDescriptor<>(
                                        DataGenerators.generatefilterUDF(UdfComplexity,dataQuantaScale,type,"filter"),
                                        type.getDataUnitType().getTypeClass()));
                                        op.setName("FinkFilter"); return op;},
                                    new Configuration(),
                                    DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkFilterProfiler(1, UdfComplexity, type));

                    case "flatmap":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkFlatMapOperator op= new FlinkFlatMapOperator(type,
                                        type,
                                        new FlatMapDescriptor<>(
                                                DataGenerators.generateUDF(UdfComplexity,dataQuantaScale,type,"flatmap"),
                                                type.getDataUnitType().getTypeClass(),
                                                type.getDataUnitType().getTypeClass()
                                        ));
                                    op.setName("FinkFlatMap"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkFlatMapProfiler(1, UdfComplexity, type));

                    case "reduce":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkReduceByOperator op= new FlinkReduceByOperator(
                                        type,
                                        new TransformationDescriptor<>(DataGenerators.generateUDF(
                                                UdfComplexity,dataQuantaScale,type,"filter"), type.getDataUnitType().getTypeClass(), type.getDataUnitType().getTypeClass()),
                                        new ReduceDescriptor<>(DataGenerators.generateBinaryUDF(UdfComplexity,dataQuantaScale,type), type.getDataUnitType().getTypeClass())
                                    );
                                    op.setName("FinkReduce"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkReduceByProfiler(1, UdfComplexity, type));

                    case "globalreduce":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkGlobalReduceOperator op= new FlinkGlobalReduceOperator(
                                        type,
                                        new ReduceDescriptor<>(DataGenerators.generateBinaryUDF(UdfComplexity,dataQuantaScale,type), type.getDataUnitType().getTypeClass())
                                );
                                    op.setName("FinkGlobalReduce"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkGlobalReduceProfiler(1000, UdfComplexity, type));

                    case "distinct":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkDistinctOperator op= new FlinkDistinctOperator(
                                        type
                                );
                                    op.setName("FinkDistinct"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkDistinctProfiler(1, type));

                    case "sort":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkSortOperator op= new FlinkSortOperator(
                                        new TransformationDescriptor<>(in->in, type.getDataUnitType().getTypeClass(), type.getDataUnitType().getTypeClass()),
                                        type
                                );
                                    op.setName("FinkSort"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkSortProfiler(1, type));

                    case "count":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkCountOperator op= new FlinkCountOperator(
                                        type
                                );
                                    op.setName("FinkCount"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkCountProfiler(1, type));

                    case "groupby":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkGroupByOperator op= new FlinkGroupByOperator(
                                        new TransformationDescriptor<>(
                                                DataGenerators.generateUDF(UdfComplexity,dataQuantaScale,type,"map"),
                                                type.getDataUnitType().getTypeClass(),
                                                type.getDataUnitType().getTypeClass()
                                        ),
                                        type,
                                        type
                                );
                                    op.setName("FinkGroupBy"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkMaterializedGroupByProfiler(1, UdfComplexity, type));

                    case "join":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkJoinOperator op= new FlinkJoinOperator(
                                        type,
                                        type,
                                        new TransformationDescriptor<>(
                                                DataGenerators.generateUDF(UdfComplexity,dataQuantaScale,type,"map"),
                                                type.getDataUnitType().getTypeClass(),
                                                type.getDataUnitType().getTypeClass()
                                        ),
                                        new TransformationDescriptor<>(
                                                DataGenerators.generateUDF(UdfComplexity,dataQuantaScale,type,"map"),
                                                type.getDataUnitType().getTypeClass(),
                                                type.getDataUnitType().getTypeClass()
                                        )
                                );
                                    op.setName("FinkJoin"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkJoinProfiler(1, UdfComplexity, type));

                    case "union":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkUnionAllOperator op= new FlinkUnionAllOperator(
                                        type
                                );
                                    op.setName("FinkUnion"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkUnionProfiler(1, type));

                    case "cartesian":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkCartesianOperator op= new FlinkCartesianOperator(
                                        type,
                                        type
                                );
                                    op.setName("FinkCartesian"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkCartesianProfiler(1, type));

                    case "callbacksink":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkLocalCallbackSink op= new FlinkLocalCallbackSink(dataQuantum -> { },type); op.setName("FinkCallBackSink"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkLocalCallbackSinkProfiler(1, type));
                    case "repeat":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkRepeatOperator op= new FlinkRepeatOperator(
                                        profilingConfig.getIterations().get(0),
                                        type
                                );
                                    op.setName("FinkRepeat"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkRepeatProfiler(1, type,profilingConfig.getIterations().get(0) ));
                    case "randomsample":
                    case "shufflesample":
                    case "bernoullisample":
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkSampleOperator op= new FlinkSampleOperator(
                                        iteration->profilingConfig.getSampleSize(),
                                        type,
                                        iteration -> 42L
                                );
                                    op.setName("FinkSample"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );

                    default:
                        logger.error("Unknown operator: " + operator);
                        return new OperatorProfilerBase(
                                (Supplier<ExecutionOperator> & Serializable) () -> {FlinkLocalCallbackSink op= new FlinkLocalCallbackSink(dataQuantum -> { },type); op.setName("FinkCallBackSink"); return op;},
                                new Configuration(),
                                DataGenerators.generateGenerator(1,type)
                        );
                        //return (SparkPlanOperatorProfilers.createSparkLocalCallbackSinkProfiler(1, type));
                }
            case "spark":
                switch (operator) {
                    case "textsource":
                        return SparkPlanOperatorProfilers.createSparkTextFileSourceProfiler(1, type);
                    case "collectionsource":
                        return SparkPlanOperatorProfilers.createSparkCollectionSourceProfiler(1000, type);
                    case "map":
                        return SparkPlanOperatorProfilers.createSparkMapProfiler(1, UdfComplexity,type);

                    case "filter":
                        return (SparkPlanOperatorProfilers.createSparkFilterProfiler(1, UdfComplexity, type));

                    case "flatmap":
                        return (SparkPlanOperatorProfilers.createSparkFlatMapProfiler(1, UdfComplexity, type));

                    case "reduce":
                        return (SparkPlanOperatorProfilers.createSparkReduceByProfiler(1, UdfComplexity, type));

                    case "globalreduce":
                        return (SparkPlanOperatorProfilers.createSparkGlobalReduceProfiler(1000, UdfComplexity, type));

                    case "distinct":
                        return (SparkPlanOperatorProfilers.createSparkDistinctProfiler(1, type));

                    case "sort":
                        return (SparkPlanOperatorProfilers.createSparkSortProfiler(1, type));

                    case "count":
                        return (SparkPlanOperatorProfilers.createSparkCountProfiler(1, type));

                    case "groupby":
                        return (SparkPlanOperatorProfilers.createSparkMaterializedGroupByProfiler(1, UdfComplexity, type));

                    case "join":
                        return (SparkPlanOperatorProfilers.createSparkJoinProfiler(1, UdfComplexity, type));

                    case "union":
                        return (SparkPlanOperatorProfilers.createSparkUnionProfiler(1, type));

                    case "cartesian":
                        return (SparkPlanOperatorProfilers.createSparkCartesianProfiler(1, type));

                    case "callbacksink":
                        return (SparkPlanOperatorProfilers.createSparkLocalCallbackSinkProfiler(1, type));
                    case "repeat":
                        return (SparkPlanOperatorProfilers.createSparkRepeatProfiler(1, type,profilingConfig.getIterations().get(0) ));
                    case "randomsample":
                        return (SparkPlanOperatorProfilers.createSparkRandomSampleProfiler(1, type,profilingConfig.getSampleSize()));
                    case "shufflesample":
                        return (SparkPlanOperatorProfilers.createSparkShuffleSampleProfiler(1, type,profilingConfig.getSampleSize()));
                    case "bernoullisample":
                        return (SparkPlanOperatorProfilers.createSparkBernoulliSampleProfiler(1, type,profilingConfig.getSampleSize()));

                    default:
                        logger.error("Unknown operator: " + operator);
                        return (SparkPlanOperatorProfilers.createSparkLocalCallbackSinkProfiler(1, type));
                }
            case "java":
                switch (operator) {
                    case "textsource":
                        return (JavaOperatorProfilers.createJavaTextFileSourceProfiler(1,type));

                    case "collectionsource":
                        return (JavaOperatorProfilers.createJavaCollectionSourceProfiler(1000,type));

                    case "map":
                        return (JavaOperatorProfilers.createJavaMapProfiler(1, UdfComplexity,type));

                    case "filter":
                        return (JavaOperatorProfilers.createJavaFilterProfiler(1, UdfComplexity,type));
                    case "flatmap":
                        return (JavaOperatorProfilers.createJavaFlatMapProfiler(1, UdfComplexity,type));

                    case "reduce":
                        return (JavaOperatorProfilers.createJavaReduceByProfiler(1, UdfComplexity, type));
                        /*return createJavaReduceByProfiler(
                                null,
                                Integer::new,
                                (s1, s2) -> { return UdfGenerators.mapIntUDF(1,1).apply(s1);},
                                Integer.class,
                                Integer.class
                        );*/

                    case "globalreduce":
                        return (JavaOperatorProfilers.createJavaGlobalReduceProfiler(1, UdfComplexity,type));

                    case "distinct":
                        return (JavaOperatorProfilers.createJavaDistinctProfiler(1,type));

                    case "sort":
                        return (JavaOperatorProfilers.createJavaSortProfiler(1, UdfComplexity,type));

                    case "count":
                        return (JavaOperatorProfilers.createJavaCountProfiler(1,type));

                    case "groupby":
                        return (JavaOperatorProfilers.createJavaMaterializedGroupByProfiler(1, UdfComplexity,type));

                    case "join":
                        return (JavaOperatorProfilers.createJavaJoinProfiler(1, UdfComplexity,type));

                    case "union":
                        return (JavaOperatorProfilers.createJavaUnionProfiler(1, type));

                    case "cartesian":
                        return (JavaOperatorProfilers.createJavaCartesianProfiler(1,type));
                    case "randomsample":
                        return (JavaOperatorProfilers.createJavaRandomSampleProfiler(1, type,profilingConfig.getSampleSize()));
                    case "shufflesample":
                        return (JavaOperatorProfilers.createJavaReservoirSampleProfiler(1, type,profilingConfig.getSampleSize()));
                    case "bernoullisample":
                        return (JavaOperatorProfilers.createJavaReservoirSampleProfiler(1, type,profilingConfig.getSampleSize()));
                    case "repeat":
                        return (JavaOperatorProfilers.createJavaRepeatProfiler(1,type,profilingConfig.getIterations().get(0)));

                    case "callbacksink":
                        return (JavaOperatorProfilers.createJavaLocalCallbackSinkProfiler(1,type));

                    case "collect":
                        return (JavaOperatorProfilers.createCollectingJavaLocalCallbackSinkProfiler(1,type));

                    default:
                        logger.error("Unknown operator: " + operator);
                        return (JavaOperatorProfilers.createJavaLocalCallbackSinkProfiler(1));
                }
            default:
                logger.error("Unknown operator: " + operator);
                return (JavaOperatorProfilers.createJavaLocalCallbackSinkProfiler(1));
        }
    }

// The below is for single operator profiling
    /**
     * Builds an operator profiling specifically for single operator profiling with fake jobs
     * @param Plateform
     * @return
     */
    private static List<? extends OperatorProfiler> exhaustiveSingleOperatorProfilingPlanBuilder(String Plateform) {
        List<String> operators = new ArrayList<String>(Arrays.asList("textsource","map", "collectionsource",  "filter", "flatmap", "reduce", "globalreduce", "distinct", "distinct-string",
                "distinct-integer", "sort", "sort-string", "sort-integer", "count", "groupby", "join", "union", "cartesian", "callbacksink", "collect",
                "word-count-split", "word-count-canonicalize", "word-count-count"));
        List<SparkOperatorProfiler> profilingOperators = new ArrayList<>();
        //List allCardinalities = this.profilingConfig.getInputCardinality();
        //List dataQuata = this.profilingConfig.getDataQuantaSize();
        //List UdfComplexity = this.profilingConfig.getUdfsComplexity();

        for (String operator : operators) {
            switch (operator) {
                case "textsource":
                    profilingOperators.add(SparkOperatorProfilers.createSparkTextFileSourceProfiler(1));
                    break;
                case "collectionsource":
                    profilingOperators.add(SparkOperatorProfilers.createSparkCollectionSourceProfiler(1));
                    break;
                case "map":
                    profilingOperators.add(SparkOperatorProfilers.createSparkMapProfiler(1, 1));
                    break;
                case "filter":
                    profilingOperators.add(SparkOperatorProfilers.createSparkFilterProfiler(1, 1));
                    break;
                case "flatmap":
                    profilingOperators.add(SparkOperatorProfilers.createSparkFlatMapProfiler(1, 1));
                    break;
                case "reduce":
                    profilingOperators.add(SparkOperatorProfilers.createSparkReduceByProfiler(1, 1));
                    break;
                case "globalreduce":
                    profilingOperators.add(SparkOperatorProfilers.createSparkGlobalReduceProfiler(1, 1));
                    break;
                case "distinct":
                case "distinct-string":
                    profilingOperators.add(SparkOperatorProfilers.createSparkDistinctProfiler(1));
                    break;
                case "distinct-integer":
                    profilingOperators.add(SparkOperatorProfilers.createSparkDistinctProfiler(
                            DataGenerators.createReservoirBasedIntegerSupplier(new ArrayList<>(), 0.7d, new Random(42)),
                            Integer.class,
                            new Configuration()
                    ));
                    break;
                case "sort":
                case "sort-string":
                    profilingOperators.add(SparkOperatorProfilers.createSparkSortProfiler(1));
                    break;
                case "sort-integer":
                    profilingOperators.add(SparkOperatorProfilers.createSparkSortProfiler(
                            DataGenerators.createReservoirBasedIntegerSupplier(new ArrayList<>(), 0.7d, new Random(42)),
                            Integer.class,
                            new Configuration()
                    ));
                    break;
                case "count":
                    profilingOperators.add(SparkOperatorProfilers.createSparkCountProfiler(1));
                    break;
                case "groupby":
                    profilingOperators.add(SparkOperatorProfilers.createSparkMaterializedGroupByProfiler(1, 1));
                    break;
                case "join":
                    profilingOperators.add(SparkOperatorProfilers.createSparkJoinProfiler(1, 1));
                    break;
                case "union":
                    profilingOperators.add(SparkOperatorProfilers.createSparkUnionProfiler(1));
                    break;
                case "cartesian":
                    profilingOperators.add(SparkOperatorProfilers.createSparkCartesianProfiler(1));
                    break;
                case "callbacksink":
                    profilingOperators.add(SparkOperatorProfilers.createSparkLocalCallbackSinkProfiler(1));
                    break;
            }
        }
        return profilingOperators;
    }


}
