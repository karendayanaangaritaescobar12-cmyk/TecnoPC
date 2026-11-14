package com.tecnopc.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationConverter;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounded, process-local protection before any HTTP Basic password verification. */
final class LoginAttemptFilter extends OncePerRequestFilter {
    private static final long WINDOW = 600_000;
    private static final int CAPACITY = 8192;
    private static final String PERMIT_ATTRIBUTE=LoginAttemptFilter.class.getName()+".permit";
    private final Clock clock;
    private final Map<Key, Bucket> buckets = new HashMap<>();
    private final BasicAuthenticationConverter converter = new BasicAuthenticationConverter();
    private int active;

    private record Key(String scope, String origin, String account) {}
    private static final class Bucket {
        int failures, active;
        long lastFailure, lastSeen, blockedUntil;
    }
    private record Permit(Bucket source, Bucket account, Bucket pair, int retryAfter) {}

    LoginAttemptFilter() { this(Clock.systemUTC()); }
    LoginAttemptFilter(Clock clock) { this.clock=clock; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        boolean internal=path.matches("/api/(admin|vendedor|tecnico)(/.*)?");
        return !internal && !isBasic(request.getHeader("Authorization"));
    }

    private static boolean isBasic(String header) {
        return header!=null && (header.equalsIgnoreCase("Basic") || header.regionMatches(true,0,"Basic ",0,6));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String account="<anonymous>";
        boolean malformed=false;
        String header=request.getHeader("Authorization");
        if(isBasic(header)) {
            try {
                if(header.length()>1024)throw new IllegalArgumentException();
                var token=converter.convert(request);
                if(token==null)throw new IllegalArgumentException();
                try {
                    // All supported account creation paths require this alphabet. This also
                    // prevents alternate spellings under a case-insensitive SQL collation.
                    if(!token.getName().matches("[a-zA-Z0-9._-]{3,60}") ||
                            token.getCredentials().toString().getBytes(StandardCharsets.UTF_8).length>72)
                        throw new IllegalArgumentException();
                    account=token.getName().toLowerCase(Locale.ROOT);
                } finally { token.eraseCredentials(); }
            } catch(AuthenticationException | IllegalArgumentException ex) {
                account="<invalid>"; malformed=true;
            }
        }
        // Never trust client-supplied forwarding headers to bypass limits.
        Permit permit=acquire(request.getRemoteAddr(),account);
        if(permit.retryAfter()>0) {
            response.setStatus(429);
            response.setHeader("Retry-After",Integer.toString(permit.retryAfter()));
            response.setHeader("Cache-Control","no-store");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"mensaje\":\"Demasiados intentos de acceso. Espera antes de volver a intentar.\"}");
            return;
        }
        request.setAttribute(PERMIT_ATTRIBUTE,permit);
        try {
            if(malformed) {
                response.setStatus(401);
                response.setHeader("WWW-Authenticate","Basic realm=\"TecnoPC\", charset=\"UTF-8\"");
                response.setHeader("Cache-Control","no-store");
            } else chain.doFilter(request,response);
        } finally {
            var authentication=SecurityContextHolder.getContext().getAuthentication();
            boolean success=authentication!=null && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken);
            if(request.getAttribute(PERMIT_ATTRIBUTE)!=null) {
                request.removeAttribute(PERMIT_ATTRIBUTE);
                finish(permit,response.getStatus()==401,success);
            }
        }
    }

    /** Runs immediately after Basic authentication, before business logic. */
    OncePerRequestFilter authenticationCompletedFilter() {
        return new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
                    FilterChain chain) throws ServletException,IOException {
                var authentication=SecurityContextHolder.getContext().getAuthentication();
                Object pending=request.getAttribute(PERMIT_ATTRIBUTE);
                if(pending instanceof Permit permit && authentication!=null && authentication.isAuthenticated()
                        && !(authentication instanceof AnonymousAuthenticationToken)) {
                    request.removeAttribute(PERMIT_ATTRIBUTE);
                    finish(permit,false,true);
                }
                chain.doFilter(request,response);
            }
        };
    }

    private synchronized Permit acquire(String origin,String account) {
        long now=clock.millis();
        Key sourceKey=new Key("source",origin,"");
        Key accountKey=new Key("account","",account);
        Key pairKey=new Key("pair",origin,account);
        Key[] keys={sourceKey,accountKey,pairKey};
        // Check existing limits before allocating state. A blocked source cannot
        // fill the map merely by changing the supplied username on each request.
        long existingBlock=0;
        for(Key key:keys){var existing=buckets.get(key);if(existing!=null)existingBlock=Math.max(existingBlock,existing.blockedUntil);}
        if(existingBlock>now)return new Permit(null,null,null,(int)((existingBlock-now+999)/1000));
        var existingSource=buckets.get(sourceKey);var existingPair=buckets.get(pairKey);
        if(active>=8 || (existingSource!=null&&existingSource.active>=2) || (existingPair!=null&&existingPair.active>=1))
            return new Permit(null,null,null,1);
        int missing=0;
        for(Key key:keys)if(!buckets.containsKey(key))missing++;
        if(buckets.size()+missing>CAPACITY) {
            buckets.entrySet().removeIf(e->e.getValue().active==0 && now-e.getValue().lastSeen>=WINDOW);
            missing=0;
            for(Key key:keys)if(!buckets.containsKey(key))missing++;
            if(buckets.size()+missing>CAPACITY)return new Permit(null,null,null,60);
        }
        Bucket source=buckets.computeIfAbsent(sourceKey,k->new Bucket());
        Bucket user=buckets.computeIfAbsent(accountKey,k->new Bucket());
        Bucket pair=buckets.computeIfAbsent(pairKey,k->new Bucket());
        // Atomic reservation prevents simultaneous requests from all passing a stale counter.
        for(Bucket bucket:new Bucket[]{source,user,pair}){bucket.active++;bucket.lastSeen=now;}
        active++;
        return new Permit(source,user,pair,0);
    }

    private synchronized void finish(Permit permit,boolean failure,boolean success) {
        long now=clock.millis();
        for(Bucket bucket:new Bucket[]{permit.source(),permit.account(),permit.pair()})bucket.active--;
        active--;
        if(failure) {
            failed(permit.source(),now,60_000,30,60);
            failed(permit.account(),now,WINDOW,20,30);
            failed(permit.pair(),now,WINDOW,5,60);
        } else if(success) {
            // Successful access clears only this origin/account pair. A known password
            // must not clear a source-wide spray or a distributed attack on an account.
            permit.pair().failures=0;
            permit.pair().blockedUntil=0;
        }
    }

    private void failed(Bucket bucket,long now,long window,int threshold,int maxSeconds) {
        if(now-bucket.lastFailure>=window)bucket.failures=0;
        bucket.lastFailure=now;bucket.lastSeen=now;
        bucket.failures=Math.min(bucket.failures+1,1000);
        if(bucket.failures>=threshold) {
            int seconds=threshold==30?maxSeconds:Math.min(maxSeconds,2 << Math.min(bucket.failures-threshold,5));
            bucket.blockedUntil=now+seconds*1000L;
        }
    }
}
