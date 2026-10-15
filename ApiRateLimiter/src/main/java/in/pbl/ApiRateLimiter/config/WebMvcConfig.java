package in.pbl.ApiRateLimiter.config;

import in.pbl.ApiRateLimiter.interceptor.RateLimiterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimiterInterceptor rateLimiterInterceptor;

    public WebMvcConfig(RateLimiterInterceptor rateLimiterInterceptor) {
        this.rateLimiterInterceptor = rateLimiterInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Protect all /api/v1/** business endpoints with RateLimiterInterceptor
        registry.addInterceptor(rateLimiterInterceptor)
                .addPathPatterns("/api/v1/**");
    }
}
