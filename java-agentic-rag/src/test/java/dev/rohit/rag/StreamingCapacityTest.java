package dev.rohit.rag;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static dev.rohit.rag.ApiTypes.*;

class StreamingCapacityTest {
    @Test void cancelledWorkerKeepsItsSlotUntilTheDependencyActuallyStops() throws Exception {
        var queries=mock(QueryService.class);
        var started=new CountDownLatch(4); var interrupted=new CountDownLatch(1);
        var stop=new CountDownLatch(1); var exited=new CountDownLatch(4);
        var failures=new ArrayList<Consumer<Throwable>>();
        when(queries.answer(any(),any(),any(),any())).thenAnswer(call->{
            started.countDown();
            try {
                // Simulate a slow dependency that cannot finish immediately on interruption.
                while(true) {
                    try { stop.await(); break; }
                    catch(InterruptedException ignored) { interrupted.countDown(); }
                }
                return new Result(UUID.randomUUID(),"finished",List.of(),false,1,0L,0L,List.of());
            } finally { exited.countDown(); }
        });
        var controller=new ProjectController(mock(SourceRepository.class),queries);
        var jwt=Jwt.withTokenValue("test").header("alg","HS256").claim("tenant_id","alpha").build();
        try(var emitters=mockConstruction(SseEmitter.class,(emitter,context)->{
            doAnswer(call->{ failures.add(call.getArgument(0)); return null; }).when(emitter).onError(any());
        })) {
            for(int i=0;i<4;i++) controller.query(jwt,new Question("question",false,false,false));
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            failures.getFirst().accept(new IOException("client disconnected"));
            assertThat(interrupted.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->controller.query(jwt,new Question("fifth",false,false,false)))
                .isInstanceOfSatisfying(ResponseStatusException.class,error->assertThat(error.getStatusCode().value()).isEqualTo(429));
        } finally {
            stop.countDown();
            assertThat(exited.await(5,TimeUnit.SECONDS)).isTrue();
            controller.close();
        }
    }
}
