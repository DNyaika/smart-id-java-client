package ee.sk.smartid.rest;

import ee.sk.smartid.exception.*;
import ee.sk.smartid.rest.dao.*;
import org.glassfish.jersey.client.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.concurrent.TimeUnit;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

public class SmartIdRestConnector implements SmartIdConnector {

  private static final Logger logger = LoggerFactory.getLogger(SmartIdRestConnector.class);
  private static final String SESSION_STATUS_URI = "/session/{sessionId}";
  private static final String CERTIFICATE_CHOICE_BY_NATIONAL_IDENTITY_PATH = "/certificatechoice/pno/{country}/{nationalIdentityNumber}";
  private static final String CERTIFICATE_CHOICE_BY_DOCUMENT_NUMBER_PATH = "/certificatechoice/document/{documentNumber}";
  private static final String SIGNATURE_BY_DOCUMENT_NUMBER_PATH = "/signature/document/{documentNumber}";
  private static final String AUTHENTICATE_BY_DOCUMENT_NUMBER_PATH = "/authentication/document/{documentNumber}";
  private static final String AUTHENTICATE_BY_NATIONAL_IDENTITY_PATH = "/authentication/pno/{country}/{nationalIdentityNumber}";
  private String endpointUrl;
  private ClientConfig clientConfig;
  private TimeUnit sessionStatusResponseSocketOpenTimeUnit;
  private long sessionStatusResponseSocketOpenTimeValue;

  public SmartIdRestConnector(String endpointUrl) {
    this.endpointUrl = endpointUrl;
  }

  public SmartIdRestConnector(String endpointUrl, ClientConfig clientConfig) {
    this(endpointUrl);
    this.clientConfig = clientConfig;
  }

  @Override
  public SessionStatus getSessionStatus(String sessionId) throws SessionNotFoundException {
    logger.debug("Getting session status for " + sessionId);
    SessionStatusRequest request = createSessionStatusRequest(sessionId);
    UriBuilder uriBuilder = UriBuilder
        .fromUri(endpointUrl)
        .path(SESSION_STATUS_URI);
    addResponseSocketOpenTimeUrlParameter(request, uriBuilder);
    URI uri = uriBuilder.build(request.getSessionId());
    try {
      SessionStatus result = prepareClient(uri).get(SessionStatus.class);
      return result;
    } catch (NotFoundException e) {
      logger.warn("Session " + request + " not found: " + e.getMessage());
      throw new SessionNotFoundException();
    }

  }

  @Override
  public CertificateChoiceResponse getCertificate(NationalIdentity identity, CertificateRequest request) {
    logger.debug("Getting certificate for " + identity);
    URI uri = UriBuilder
        .fromUri(endpointUrl)
        .path(CERTIFICATE_CHOICE_BY_NATIONAL_IDENTITY_PATH)
        .build(identity.getCountryCode(), identity.getNationalIdentityNumber());
    return postCertificateRequest(uri, request);
  }

  @Override
  public CertificateChoiceResponse getCertificate(String documentNumber, CertificateRequest request) {
    logger.debug("Getting certificate for document " + documentNumber);
    URI uri = UriBuilder
        .fromUri(endpointUrl)
        .path(CERTIFICATE_CHOICE_BY_DOCUMENT_NUMBER_PATH)
        .build(documentNumber);
    return postCertificateRequest(uri, request);
  }

  @Override
  public SignatureSessionResponse sign(String documentNumber, SignatureSessionRequest request) {
    logger.debug("Signing for document " + documentNumber);
    URI uri = UriBuilder
        .fromUri(endpointUrl)
        .path(SIGNATURE_BY_DOCUMENT_NUMBER_PATH)
        .build(documentNumber);
    try {
      return postRequest(uri, request, SignatureSessionResponse.class);
    } catch (NotFoundException e) {
      logger.warn("User account not found for signing with document " + documentNumber);
      throw new UserAccountNotFoundException();
    } catch (ForbiddenException e) {
      logger.warn("No permission to issue the request");
      throw new RequestForbiddenException();
    }
  }

  @Override
  public AuthenticationSessionResponse authenticate(String documentNumber, AuthenticationSessionRequest request) {
    logger.debug("Authenticating for document " + documentNumber);
    URI uri = UriBuilder
        .fromUri(endpointUrl)
        .path(AUTHENTICATE_BY_DOCUMENT_NUMBER_PATH)
        .build(documentNumber);
    return postAuthenticationRequest(uri, request);
  }

  @Override
  public AuthenticationSessionResponse authenticate(NationalIdentity identity, AuthenticationSessionRequest request) {
    logger.debug("Authenticating for " + identity);
    URI uri = UriBuilder
        .fromUri(endpointUrl)
        .path(AUTHENTICATE_BY_NATIONAL_IDENTITY_PATH)
        .build(identity.getCountryCode(), identity.getNationalIdentityNumber());
    return postAuthenticationRequest(uri, request);
  }

  @Override
  public void setSessionStatusResponseSocketOpenTime(TimeUnit sessionStatusResponseSocketOpenTimeUnit, long sessionStatusResponseSocketOpenTimeValue) {
    this.sessionStatusResponseSocketOpenTimeUnit = sessionStatusResponseSocketOpenTimeUnit;
    this.sessionStatusResponseSocketOpenTimeValue = sessionStatusResponseSocketOpenTimeValue;
  }

  private Invocation.Builder prepareClient(URI uri) {
    Client client = clientConfig == null ? ClientBuilder.newClient() : ClientBuilder.newClient(clientConfig);
    Invocation.Builder builder = client
        .register(new LoggingFilter())
        .target(uri)
        .request()
        .accept(APPLICATION_JSON_TYPE);
    return builder;
  }

  private CertificateChoiceResponse postCertificateRequest(URI uri, CertificateRequest request) {
    try {
      return postRequest(uri, request, CertificateChoiceResponse.class);
    } catch (NotFoundException e) {
      logger.warn("Certificate not found for URI " + uri + ": " + e.getMessage());
      throw new CertificateNotFoundException();
    } catch (ForbiddenException e) {
      logger.warn("No permission to issue the request");
      throw new RequestForbiddenException();
    }
  }

  private AuthenticationSessionResponse postAuthenticationRequest(URI uri, AuthenticationSessionRequest request) {
    try {
      return postRequest(uri, request, AuthenticationSessionResponse.class);
    } catch (NotFoundException e) {
      logger.warn("User account not found for URI " + uri + ": " + e.getMessage());
      throw new UserAccountNotFoundException();
    } catch (ForbiddenException e) {
      logger.warn("No permission to issue the request");
      throw new RequestForbiddenException();
    }
  }

  private <T, V> T postRequest(URI uri, V request, Class<T> responseType) {
    try {
      Entity<V> requestEntity = Entity.entity(request, MediaType.APPLICATION_JSON);
      T result = prepareClient(uri).post(requestEntity, responseType);
      return result;
    } catch (NotAuthorizedException e) {
      logger.warn("Request is unauthorized for URI " + uri + ": " + e.getMessage());
      throw new UnauthorizedException();
    } catch (BadRequestException e) {
      logger.warn("Request is invalid for URI " + uri + ": " + e.getMessage());
      throw new InvalidParametersException();
    } catch (ClientErrorException e) {
      if (e.getResponse().getStatus() == 480) {
        logger.warn("Client-side API is too old and not supported anymore");
        throw new ClientNotSupportedException();
      }
      throw e;
    } catch (ServerErrorException e) {
      if (e.getResponse().getStatus() == 580) {
        logger.warn("Server is under maintenance, retry later");
        throw new ServerMaintenanceException();
      }
      throw e;
    }
  }


  private SessionStatusRequest createSessionStatusRequest(String sessionId) {
    SessionStatusRequest request = new SessionStatusRequest(sessionId);
    if (sessionStatusResponseSocketOpenTimeUnit != null && sessionStatusResponseSocketOpenTimeValue > 0) {
      request.setResponseSocketOpenTime(sessionStatusResponseSocketOpenTimeUnit, sessionStatusResponseSocketOpenTimeValue);
    }
    return request;
  }

  private void addResponseSocketOpenTimeUrlParameter(SessionStatusRequest request, UriBuilder uriBuilder) {
    if (request.isResponseSocketOpenTimeSet()) {
      TimeUnit timeUnit = request.getResponseSocketOpenTimeUnit();
      long timeValue = request.getResponseSocketOpenTimeValue();
      long queryTimeoutInMilliseconds = timeUnit.toMillis(timeValue);
      uriBuilder.queryParam("timeoutMs", queryTimeoutInMilliseconds);
    }
  }
}
