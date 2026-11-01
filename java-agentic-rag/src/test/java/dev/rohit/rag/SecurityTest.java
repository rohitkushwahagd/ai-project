package dev.rohit.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest(controllers=ProjectController.class,properties="app.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Import(SecurityConfig.class)
class SecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean SourceRepository repository;
    @MockitoBean QueryService queries;
    @Test void anonymousCannotIngest() throws Exception {
        mvc.perform(post("/ingest").contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }
    @Test void documentListingRequiresReadScopeAndUsesJwtTenant() throws Exception {
        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/documents").with(jwt().jwt(j->j.claim("tenant_id","one"))
            .authorities(new SimpleGrantedAuthority("SCOPE_rag.write")))).andExpect(status().isForbidden());
        when(repository.list("one",2)).thenReturn(java.util.List.of());
        mvc.perform(get("/api/documents?page=2").with(jwt().jwt(j->j.claim("tenant_id","one"))
            .authorities(new SimpleGrantedAuthority("SCOPE_rag.read")))).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(repository).list("one",2);
    }
    @Test void readOnlyTokenCannotIngest() throws Exception {
        mvc.perform(post("/ingest").with(jwt().jwt(j->j.claim("tenant_id","one"))
            .authorities(new SimpleGrantedAuthority("SCOPE_rag.read"))).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
    @Test void validatesQuestionBeforeModelInvocation() throws Exception {
        mvc.perform(post("/query").with(jwt().jwt(j->j.claim("tenant_id","one"))
            .authorities(new SimpleGrantedAuthority("SCOPE_rag.read"))).contentType("application/json")
            .content("{\"question\":\" \"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(queries);
    }
    @Test void readOnlyTokenCannotReplaceOrDelete() throws Exception {
        var reader=jwt().jwt(j->j.claim("tenant_id","one")).authorities(new SimpleGrantedAuthority("SCOPE_rag.read"));
        String path="/sources/"+java.util.UUID.randomUUID();
        mvc.perform(delete(path).with(reader)).andExpect(status().isForbidden());
        mvc.perform(put(path).with(reader).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }
    @Test void metricsAreNotPublicOrGrantedToOrdinaryReaders() throws Exception {
        mvc.perform(get("/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/metrics").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_rag.read"))))
                .andExpect(status().isForbidden());
    }
}
