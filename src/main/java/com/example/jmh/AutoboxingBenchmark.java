package com.example.jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Autoboxing overhead: summing with a primitive {@code long} accumulator
 * vs a boxed {@code Long} accumulator.
 *
 * The boxed version allocates a new Long object on (almost) every iteration
 * of the loop — values outside the Long cache [-128, 127] can't be reused.
 * This is still very much a thing in modern Java (21+): the JIT can
 * sometimes eliminate boxing via escape analysis, but not in accumulation
 * loops like this one.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class AutoboxingBenchmark {

    @Param({"10000"})
    private int iterations;

    @Benchmark
    public long primitiveSum() {
        long sum = 0L;              // stays in a register / on the stack
        for (int i = 0; i < iterations; i++) {
            sum += i;
        }
        return sum;
    }

    @Benchmark
    public Long boxedSum() {
        Long sum = 0L;              // unbox + add + box, every single lap
        for (int i = 0; i < iterations; i++) {
            sum += i;
        }
        return sum;
    }
}
