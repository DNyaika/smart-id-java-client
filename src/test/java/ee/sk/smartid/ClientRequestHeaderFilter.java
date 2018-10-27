package ee.sk.smartid;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.MultivaluedMap;
import java.util.Map;

public class ClientRequestHeaderFilter implements ClientRequestFilter {

  private Map<String, String> headersToAdd;

  public ClientRequestHeaderFilter(Map<String, String> headersToAdd) {
    this.headersToAdd = headersToAdd;
  }

  @Override
  public void filter(ClientRequestContext requestContext) {
    MultivaluedMap headers = requestContext.getHeaders();
    for (Map.Entry<String, String> entry : headersToAdd.entrySet()) {
      headers.putSingle(entry.getKey(), entry.getValue());
    }
  }

}
