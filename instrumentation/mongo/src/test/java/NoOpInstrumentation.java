import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import net.bytebuddy.agent.builder.AgentBuilder;

@AutoService(Instrumenter.class)
public class NoOpInstrumentation implements Instrumenter {

  @Override
  public AgentBuilder instrument(final AgentBuilder agentBuilder) {
    return agentBuilder;
  }
}
