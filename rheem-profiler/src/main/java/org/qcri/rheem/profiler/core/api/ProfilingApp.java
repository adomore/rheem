package org.qcri.rheem.profiler.core.api;

import org.qcri.rheem.profiler.core.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Created by migiwara on 04/07/17.
 */
public class ProfilingApp {

    public static void main(String[] args){
        //String profileTesting = "single_operator_profiling";
        String profileTesting;
        if (args.length==1)
            profileTesting=args[0];
        else
            profileTesting = "exhaustive_profiling";
        List<Topology> topologies;
        ProfilingConfig profilingConfig;
        List< ? extends OperatorProfiler> operatorProfilers;
        List<Shape> shapes = new ArrayList<>();
        List<List<PlanProfiler>> planProfilers;
        List<List<OperatorProfiler>> planProfilersSinks;

        int nodeNumber = 3
                ;

        switch (profileTesting){
            case "single_operator_profiling":
                topologies = TopologyGenerator.generateTopology(1);
                profilingConfig = ProfilingConfigurer.exhaustiveProfilingConfig();
                operatorProfilers = ProfilingPlanBuilder.PlanBuilder(topologies.get(0),profilingConfig);
                ProfilingRunner.SingleOperatorProfiling(operatorProfilers,profilingConfig);
            case "exhaustive_profiling":
                profilingConfig = ProfilingConfigurer.exhaustiveProfilingConfig();

                // Initialize the generator
                TopologyGenerator topologyGenerator =new TopologyGenerator(nodeNumber,profilingConfig);

                // Generate the topologies
                topologyGenerator.startGeneration();
                topologies = topologyGenerator.getTopologyList();

                // Instantiate the topologies
                InstantiateTopology.instantiateTopology(topologies, nodeNumber);

                // create shapes
                shapes = topologies.stream().map(t -> new Shape(t)).collect(Collectors.toList());
                shapes.stream().forEach(s -> s.populateShape(s.getSinkTopology()));

                // populate shapes
                profilingConfig = ProfilingConfigurer.exhaustiveProfilingConfig();
                planProfilers = ProfilingPlanBuilder.exhaustiveProfilingPlanBuilder(shapes,profilingConfig);
                ProfilingRunner.exhaustiveProfiling(planProfilers,profilingConfig);
                //System.out.println(result.toCsvString())
        }

    }
}