package com.remondis.remap.benchmark;

import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Runs the ReMap JMH benchmarks. Results are printed to the console and written to
 * <code>target/jmh-result.json</code>.
 *
 * <p>
 * The benchmark JVMs forked by JMH require the test classpath on <code>java.class.path</code>, so the runner must be
 * started with a plain <code>java</code> command instead of <code>exec:java</code>:
 *
 * <pre>
 * mvn test-compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -Dmdep.includeScope=test
 * java -cp "target/classes:target/test-classes:$(cat target/classpath.txt)" \
 *     com.remondis.remap.benchmark.BenchmarkRunner
 * </pre>
 *
 * (On Windows use <code>;</code> as path separator.) An optional first argument is used as benchmark include regex,
 * e.g. <code>mapCollections</code>.
 * </p>
 */
public class BenchmarkRunner {

  public static void main(String[] args) throws RunnerException {
    Options options = new OptionsBuilder().include(args.length > 0 ? args[0] : MappingBenchmark.class.getSimpleName())
        .jvmArgsAppend("--add-opens", "java.base/java.lang=ALL-UNNAMED")
        .resultFormat(ResultFormatType.JSON)
        .result("target/jmh-result.json")
        .build();
    new Runner(options).run();
  }
}
