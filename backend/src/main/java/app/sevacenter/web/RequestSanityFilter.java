package app.sevacenter.web;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First filter in the chain: rejects malformed request parameters with a 400 before anything
 * else touches them. Found by DAST (M2), both as 500s:
 * <ul>
 *   <li>Tomcat 11 throws {@code InvalidParameterException} (an {@link IllegalStateException}) for
 *       a query like {@code ?=x} the first time anything reads the parameters.</li>
 *   <li>A NUL character passes bean validation but Postgres rejects it in text, so it surfaced
 *       as a database error. JSON bodies are covered separately ({@link NulRejectingStrings}).</li>
 * </ul>
 */
public class RequestSanityFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Map<String, String[]> params;
        try {
            params = request.getParameterMap();
        } catch (IllegalStateException e) {
            reject(response);
            return;
        }
        for (Map.Entry<String, String[]> param : params.entrySet()) {
            if (param.getKey().indexOf('\0') >= 0) {
                reject(response);
                return;
            }
            for (String value : param.getValue()) {
                if (value != null && value.indexOf('\0') >= 0) {
                    reject(response);
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":400,\"error\":\"Bad Request\"}");
    }
}
