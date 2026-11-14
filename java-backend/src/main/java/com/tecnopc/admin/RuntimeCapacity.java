package com.tecnopc.admin;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/** Process-local operational counters, with no usernames, payloads or references. */
public final class RuntimeCapacity {
    private final AtomicInteger active=new AtomicInteger();
    private final AtomicInteger peak=new AtomicInteger();
    private final LongAdder completed=new LongAdder(),rejected=new LongAdder(),errors=new LongAdder(),oversized=new LongAdder(),nanos=new LongAdder();
    void enter(){peak.accumulateAndGet(active.incrementAndGet(),Math::max);}
    void finish(long elapsed,boolean error){active.decrementAndGet();completed.increment();nanos.add(elapsed);if(error)errors.increment();}
    void reject(int status){if(status==503)rejected.increment();if(status==413)oversized.increment();}
    public Map<String,Object> snapshot(){long count=completed.sum();return Map.of("activos",active.get(),"pico",peak.get(),"completados",count,
            "rechazadosPorCapacidad",rejected.sum(),"errores",errors.sum(),"cuerposExcedidos",oversized.sum(),"latenciaMediaMs",count==0?0:nanos.sum()/count/1_000_000);}
}
