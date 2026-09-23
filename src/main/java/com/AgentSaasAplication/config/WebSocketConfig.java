package com.AgentSaasAplication.config;

import com.AgentSaasAplication.gateway.AgentBridgeHandler;
import com.AgentSaasAplication.gateway.BridgeAuthInterceptor;
import com.AgentSaasAplication.gateway.RunnerTerminalAuthInterceptor;
import com.AgentSaasAplication.gateway.RunnerTerminalWsHandler;
import com.AgentSaasAplication.gateway.TaskLogStreamAuthInterceptor;
import com.AgentSaasAplication.gateway.TaskLogStreamHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AgentBridgeHandler agentBridgeHandler;
    private final BridgeAuthInterceptor bridgeAuthInterceptor;
    private final TaskLogStreamHandler taskLogStreamHandler;
    private final TaskLogStreamAuthInterceptor taskLogStreamAuthInterceptor;
    private final RunnerTerminalWsHandler runnerTerminalWsHandler;
    private final RunnerTerminalAuthInterceptor runnerTerminalAuthInterceptor;

    // Tarayıcı kaynaklı WS bağlantıları yalnızca gerçek frontend origin'lerine
    // kısıtlanıyor: kimlik/yetki zaten interceptor'da doğrulansa da bu bir savunma
    // derinliği katmanı (CORS ile aynı liste, bkz. SecurityConfig).
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,http://localhost:4200}")
    private String[] allowedOrigins;

    public WebSocketConfig(AgentBridgeHandler agentBridgeHandler,
                            BridgeAuthInterceptor bridgeAuthInterceptor,
                            TaskLogStreamHandler taskLogStreamHandler,
                            TaskLogStreamAuthInterceptor taskLogStreamAuthInterceptor,
                            RunnerTerminalWsHandler runnerTerminalWsHandler,
                            RunnerTerminalAuthInterceptor runnerTerminalAuthInterceptor) {
        this.agentBridgeHandler = agentBridgeHandler;
        this.bridgeAuthInterceptor = bridgeAuthInterceptor;
        this.taskLogStreamHandler = taskLogStreamHandler;
        this.taskLogStreamAuthInterceptor = taskLogStreamAuthInterceptor;
        this.runnerTerminalWsHandler = runnerTerminalWsHandler;
        this.runnerTerminalAuthInterceptor = runnerTerminalAuthInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentBridgeHandler, "/ws/agent-bridge")
                .addInterceptors(bridgeAuthInterceptor)
                .setAllowedOrigins("*"); // local agent'lar tarayıcı istemcisi değil, CORS kısıtı anlamsız

        // Frontend'in bir task'ın canlı log akışını izlemesi için.
        registry.addHandler(taskLogStreamHandler, "/ws/tasks/*/logs")
                .addInterceptors(taskLogStreamAuthInterceptor)
                .setAllowedOrigins(allowedOrigins);

        // Runner'lara canlı terminal erişimi — tarayıcı ucu (bkz. RunnerTerminalWsHandler Javadoc'u).
        registry.addHandler(runnerTerminalWsHandler, "/ws/runners/*/terminal")
                .addInterceptors(runnerTerminalAuthInterceptor)
                .setAllowedOrigins(allowedOrigins);
    }
}