/*
 * (C) Copyright 2016-2018, by Dimitrios Michail and Contributors.
 *
 * JGraphT : a free Java graph-theory library
 *
 * This program and the accompanying materials are dual-licensed under
 * either
 *
 * (a) the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation, or (at your option) any
 * later version.
 *
 * or (per the licensee's choosing)
 *
 * (b) the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation.
 */
package org.jgrapht.perf.shortestpath;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.alg.shortestpath.*;
import org.jgrapht.alg.util.*;
import org.jgrapht.generate.*;
import org.jgrapht.graph.*;
import org.jgrapht.traverse.*;
import org.jgrapht.util.*;
import org.openjdk.jmh.runner.*;

import junit.framework.*;

/**
 * A small benchmark comparing Dijkstra like algorithms. The benchmark creates a random graph and
 * computes all-pairs shortest paths.
 * 
 * @author Dimitrios Michail
 */
public class DijkstraShortestPathPerformanceTest
    extends TestCase
{
    private static final int PERF_BENCHMARK_VERTICES_COUNT = 250;
    private static final double PERF_BENCHMARK_EDGES_PROP = 0.3;
    private static final int WARMUP_REPEAT = 5;
    private static final int REPEAT = 10;
    private static final long SEED = 13l;

    private static abstract class BenchmarkBase
    {
        protected Random rng = new Random(SEED);
        protected GraphGenerator<Integer, DefaultWeightedEdge, Integer> generator = null;
        protected Graph<Integer, DefaultWeightedEdge> graph;

        abstract ShortestPathAlgorithm<Integer, DefaultWeightedEdge> createSolver(
            Graph<Integer, DefaultWeightedEdge> graph);

        public void setup()
        {
            if (generator == null) {
                // lazily construct generator
                generator = new GnpRandomGraphGenerator<>(
                    PERF_BENCHMARK_VERTICES_COUNT, PERF_BENCHMARK_EDGES_PROP, rng, false);
            }

            DirectedWeightedPseudograph<Integer, DefaultWeightedEdge> weightedGraph =
                new DirectedWeightedPseudograph<>(DefaultWeightedEdge.class);
            this.graph = weightedGraph;

            generator.generateGraph(graph, new IntegerVertexFactory(), null);

            for (DefaultWeightedEdge e : weightedGraph.edgeSet()) {
                weightedGraph.setEdgeWeight(e, rng.nextDouble());
            }
        }

        public void run()
        {
            ShortestPathAlgorithm<Integer, DefaultWeightedEdge> sp = createSolver(graph);
            for (Integer v : graph.vertexSet()) {
                for (Integer u : graph.vertexSet()) {
                    sp.getPath(v, u);
                }
            }
        }
    }

    public static class DijkstraBenchmark
        extends BenchmarkBase
    {
        @Override
        ShortestPathAlgorithm<Integer, DefaultWeightedEdge> createSolver(
            Graph<Integer, DefaultWeightedEdge> graph)
        {
            return new DijkstraShortestPath<>(graph);
        }

        @Override
        public String toString()
        {
            return "Dijkstra";
        }
    }

    public static class ClosestFirstIteratorBenchmark
        extends BenchmarkBase
    {
        @Override
        ShortestPathAlgorithm<Integer, DefaultWeightedEdge> createSolver(
            Graph<Integer, DefaultWeightedEdge> graph)
        {
            return new ShortestPathAlgorithm<Integer, DefaultWeightedEdge>()
            {

                @Override
                public GraphPath<Integer, DefaultWeightedEdge> getPath(Integer source, Integer sink)
                {
                    /*
                     * We do not really return a result here, just reach the target.
                     */
                    ClosestFirstIterator<Integer, DefaultWeightedEdge> iter =
                        new ClosestFirstIterator<>(graph, source, Double.POSITIVE_INFINITY);
                    while (iter.hasNext()) {
                        Integer vertex = iter.next();
                        if (vertex.equals(sink)) {
                            return null;
                        }
                    }
                    return null;
                }

                @Override
                public double getPathWeight(Integer source, Integer sink)
                {
                    GraphPath<Integer, DefaultWeightedEdge> p = getPath(source, sink);
                    if (p == null) {
                        return Double.POSITIVE_INFINITY;
                    } else {
                        return p.getWeight();
                    }
                }

                public org.jgrapht.alg.interfaces.ShortestPathAlgorithm.SingleSourcePaths<Integer,
                    DefaultWeightedEdge> getPaths(Integer source)
                {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public String toString()
        {
            return "Dijkstra with ClosestFirstIterator";
        }
    }

    public static class BidirectionalDijkstraBenchmark
        extends BenchmarkBase
    {
        @Override
        ShortestPathAlgorithm<Integer, DefaultWeightedEdge> createSolver(
            Graph<Integer, DefaultWeightedEdge> graph)
        {
            return new BidirectionalDijkstraShortestPath<>(graph);

        }

        @Override
        public String toString()
        {
            return "Bidirectional Dijkstra";
        }
    }

    public static class AStarNoHeuristicBenchmark
        extends BenchmarkBase
    {
        @Override
        ShortestPathAlgorithm<Integer, DefaultWeightedEdge> createSolver(
            Graph<Integer, DefaultWeightedEdge> graph)
        {
            return new AStarShortestPath<>(graph, (u, t) -> 0d);

        }

        @Override
        public String toString()
        {
            return "A* no heuristic";
        }
    }

    public static class ALTBenchmark
        extends BenchmarkBase
    {
        private int totalLandmarks;

        public ALTBenchmark(int totalLandmarks)
        {
            this.totalLandmarks = totalLandmarks;
        }

        @Override
        ShortestPathAlgorithm<Integer, DefaultWeightedEdge> createSolver(
            Graph<Integer, DefaultWeightedEdge> graph)
        {
            Integer[] vertices = graph.vertexSet().toArray(new Integer[0]);
            Set<Integer> landmarks = new HashSet<>();
            while (landmarks.size() < totalLandmarks) {
                landmarks.add(vertices[rng.nextInt(graph.vertexSet().size())]);
            }
            return new AStarShortestPath<>(graph, new ALTAdmissibleHeuristic<>(graph, landmarks));
        }

        @Override
        public String toString()
        {
            return "A* with ALT heuristic (" + totalLandmarks + " random landmarks)";
        }
    }

    public void testBenchmark()
        throws RunnerException
    {
        System.out.println("All-Pairs Shortest Paths Benchmark");
        System.out.println("---------");
        System.out.println(
            "Using G(n,p) random graph with n = " + PERF_BENCHMARK_VERTICES_COUNT + ", p = "
                + PERF_BENCHMARK_EDGES_PROP);
        System.out.println("Warmup phase " + WARMUP_REPEAT + " executions");
        System.out.println("Averaging results over " + REPEAT + " executions");

        List<Supplier<BenchmarkBase>> algFactory = new ArrayList<>();
        algFactory.add(() -> new ClosestFirstIteratorBenchmark());
        algFactory.add(() -> new DijkstraBenchmark());
        algFactory.add(() -> new AStarNoHeuristicBenchmark());
        algFactory.add(() -> new ALTBenchmark(1));
        algFactory.add(() -> new ALTBenchmark(5));
        algFactory.add(() -> new BidirectionalDijkstraBenchmark());

        for (Supplier<BenchmarkBase> alg : algFactory) {

            System.gc();
            StopWatch watch = new StopWatch();

            BenchmarkBase benchmark = alg.get();
            System.out.printf("%-50s :", benchmark.toString());

            for (int i = 0; i < WARMUP_REPEAT; i++) {
                System.out.print("-");
                benchmark.setup();
                benchmark.run();
            }
            double avgGraphCreate = 0d;
            double avgExecution = 0d;
            for (int i = 0; i < REPEAT; i++) {
                System.out.print("+");
                watch.start();
                benchmark.setup();
                avgGraphCreate += watch.getElapsed(TimeUnit.MILLISECONDS);
                watch.start();
                benchmark.run();
                avgExecution += watch.getElapsed(TimeUnit.MILLISECONDS);
            }
            avgGraphCreate /= REPEAT;
            avgExecution /= REPEAT;

            System.out.print(" -> ");
            System.out
                .printf("setup %.3f (ms) | execution %.3f (ms)\n", avgGraphCreate, avgExecution);
        }

    }

}
