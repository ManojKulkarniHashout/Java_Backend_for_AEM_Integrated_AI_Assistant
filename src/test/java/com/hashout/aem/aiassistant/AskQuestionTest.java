package com.hashout.aem.aiassistant;

import com.hashout.aem.aiassistant.model.ChatRequest;
import com.hashout.aem.aiassistant.model.ChatResponse;
import com.microsoft.azure.functions.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for AskQuestion functionality.
 */
public class AskQuestionTest {
    
    /**
     * Test AskQuestion endpoint with invalid request body
     */
    @Test
    public void testAskQuestionWithEmptyBody() throws Exception {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        final Optional<String> queryBody = Optional.empty();
        doReturn(queryBody).when(req).getBody();

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        final ExecutionContext context = mock(ExecutionContext.class);
        doReturn(Logger.getGlobal()).when(context).getLogger();

        // Invoke
        final HttpResponseMessage ret = new Function().askQuestion(req, context);

        // Verify
        assertEquals(ret.getStatus(), HttpStatus.BAD_REQUEST);
        assertTrue(ret.getBody().toString().contains("Request body is required"));
    }
    
    /**
     * Test AskQuestion endpoint with invalid JSON
     */
    @Test
    public void testAskQuestionWithInvalidJson() throws Exception {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        final Optional<String> queryBody = Optional.of("invalid json");
        doReturn(queryBody).when(req).getBody();

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        final ExecutionContext context = mock(ExecutionContext.class);
        doReturn(Logger.getGlobal()).when(context).getLogger();

        // Invoke
        final HttpResponseMessage ret = new Function().askQuestion(req, context);

        // Verify
        assertEquals(ret.getStatus(), HttpStatus.BAD_REQUEST);
        assertTrue(ret.getBody().toString().contains("Invalid JSON format"));
    }
    
    /**
     * Test AskQuestion endpoint with empty question
     */
    @Test
    public void testAskQuestionWithEmptyQuestion() throws Exception {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        final Optional<String> queryBody = Optional.of("{\"question\": \"\"}");
        doReturn(queryBody).when(req).getBody();

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        final ExecutionContext context = mock(ExecutionContext.class);
        doReturn(Logger.getGlobal()).when(context).getLogger();

        // Invoke
        final HttpResponseMessage ret = new Function().askQuestion(req, context);

        // Verify
        assertEquals(ret.getStatus(), HttpStatus.BAD_REQUEST);
        assertTrue(ret.getBody().toString().contains("Question is required and cannot be empty"));
    }
    
    /**
     * Test ChatRequest model validation
     */
    @Test
    public void testChatRequestValidation() {
        // Test valid request
        ChatRequest validRequest = new ChatRequest("What is AEM?");
        assertTrue(validRequest.isValid());
        
        // Test empty question
        ChatRequest emptyRequest = new ChatRequest("");
        assertFalse(emptyRequest.isValid());
        
        // Test null question
        ChatRequest nullRequest = new ChatRequest(null);
        assertFalse(nullRequest.isValid());
        
        // Test whitespace-only question
        ChatRequest whitespaceRequest = new ChatRequest("   ");
        assertFalse(whitespaceRequest.isValid());
    }
    
    /**
     * Test ChatResponse model
     */
    @Test
    public void testChatResponseModel() {
        List<String> sources = Arrays.asList("/content/page1", "/content/page2");
        ChatResponse response = new ChatResponse("Answer", sources, "high", "2023-01-01T12:00:00");
        
        assertEquals("Answer", response.getAnswer());
        assertEquals(sources, response.getSources());
        assertEquals("high", response.getConfidence());
        assertEquals("2023-01-01T12:00:00", response.getTimestamp());
    }
}