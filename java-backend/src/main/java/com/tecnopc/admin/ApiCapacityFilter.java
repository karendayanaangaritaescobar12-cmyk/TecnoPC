package com.tecnopc.admin;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reject excess work before authentication/SQL; static assets remain available. */
final class ApiCapacityFilter extends OncePerRequestFilter {
    private static final int MAX_BODY=65_536;
    private final Semaphore total=new Semaphore(12);
    private final Semaphore publicRequests=new Semaphore(6);
    private final Semaphore privateRequests=new Semaphore(4);
    private final Semaphore webhooks=new Semaphore(2);
    private final RuntimeCapacity capacity;
    ApiCapacityFilter(){this(new RuntimeCapacity());}
    ApiCapacityFilter(RuntimeCapacity capacity){this.capacity=capacity;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
            FilterChain chain) throws ServletException,IOException {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        Semaphore lane=path.equals("/api/public/pagos/webhook")?webhooks:
                path.startsWith("/api/public/")?publicRequests:privateRequests;
        if(!lane.tryAcquire()){reject(response,503,"La tienda está ocupada. Espera antes de volver a intentar.");return;}
        boolean acquired=total.tryAcquire();
        long started=System.nanoTime();boolean entered=false;boolean exceptional=false;
        try {
            if(!acquired){reject(response,503,"La tienda está ocupada. Espera antes de volver a intentar.");return;}
            capacity.enter();entered=true;
            if(request.getContentLengthLong()>MAX_BODY){reject(response,413,"Solicitud demasiado grande");return;}
            byte[] body=request.getInputStream().readNBytes(MAX_BODY+1);
            if(body.length>MAX_BODY){reject(response,413,"Solicitud demasiado grande");return;}
            chain.doFilter(new HttpServletRequestWrapper(request){
                @Override public int getContentLength(){return body.length;}
                @Override public long getContentLengthLong(){return body.length;}
                @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8));}
                @Override public ServletInputStream getInputStream(){
                    var input=new ByteArrayInputStream(body);
                    return new ServletInputStream(){
                        @Override public int read(){return input.read();}
                        @Override public int read(byte[] bytes,int offset,int length){return input.read(bytes,offset,length);}
                        @Override public boolean isFinished(){return input.available()==0;}
                        @Override public boolean isReady(){return true;}
                        @Override public void setReadListener(ReadListener listener){throw new UnsupportedOperationException();}
                    };
                }
            },response);
        } catch(IOException|ServletException|RuntimeException ex){exceptional=true;throw ex;}
        finally {if(entered)capacity.finish(System.nanoTime()-started,exceptional||response.getStatus()>=500);if(acquired)total.release();lane.release();}
    }
    private void reject(HttpServletResponse response,int status,String message) throws IOException {
        capacity.reject(status);
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control","no-store");if(status==503)response.setHeader("Retry-After","2");
        response.getWriter().write("{\"mensaje\":\""+message+"\"}");
    }
}
