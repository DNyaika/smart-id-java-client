package ee.sk.smartid;

import ee.sk.smartid.exception.InvalidParametersException;
import ee.sk.smartid.exception.TechnicalErrorException;
import ee.sk.smartid.rest.SessionStatusPoller;
import ee.sk.smartid.rest.SmartIdConnector;
import ee.sk.smartid.rest.dao.CertificateChoiceResponse;
import ee.sk.smartid.rest.dao.CertificateRequest;
import ee.sk.smartid.rest.dao.NationalIdentity;
import ee.sk.smartid.rest.dao.SessionCertificate;
import ee.sk.smartid.rest.dao.SessionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.security.provider.X509Factory;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class CertificateRequestBuilder {

  private static final Logger logger = LoggerFactory.getLogger(CertificateRequestBuilder.class);
  private SmartIdConnector connector;
  private String relyingPartyUUID;
  private String relyingPartyName;
  private NationalIdentity nationalIdentity;
  private String certificateLevel;
  private String documentNumber;

  public CertificateRequestBuilder(SmartIdConnector connector) {
    logger.debug("Instantiating certificate request builder");
    this.connector = connector;
  }

  public CertificateRequestBuilder withNationalIdentity(NationalIdentity nationalIdentity) {
    this.nationalIdentity = nationalIdentity;
    return this;
  }

  public CertificateRequestBuilder withCertificateLevel(String certificateLevel) {
    this.certificateLevel = certificateLevel;
    return this;
  }

  public CertificateRequestBuilder withRelyingPartyUUID(String relyingPartyUUID) {
    this.relyingPartyUUID = relyingPartyUUID;
    return this;
  }

  public CertificateRequestBuilder withRelyingPartyName(String relyingPartyName) {
    this.relyingPartyName = relyingPartyName;
    return this;
  }

  public CertificateRequestBuilder withDocumentNumber(String documentNumber) {
    this.documentNumber = documentNumber;
    return this;
  }

  public X509Certificate fetch() {
    logger.debug("Starting to fetch certificate");
    CertificateRequest request = createCertificateRequest();
    CertificateChoiceResponse certificateChoiceResponse = fetchCertificateChoiceSessionResponse(request);

    SessionStatus sessionStatus = new SessionStatusPoller(connector).fetchFinalSessionStatus(certificateChoiceResponse.getSessionId());
    SessionCertificate certificate = sessionStatus.getCertificate();
    String certificateValue = certificate.getValue();

    X509Certificate cert = parseX509Certificate(certificateValue);
    return cert;
  }

  private CertificateChoiceResponse fetchCertificateChoiceSessionResponse(CertificateRequest request) {
    if (isNotEmpty(documentNumber)) {
      return connector.getCertificate(documentNumber, request);
    } else if (nationalIdentity != null) {
      return connector.getCertificate(nationalIdentity, request);
    } else {
      logger.error("Either document number or national identity must be set");
      throw new InvalidParametersException("Either document number or national identity must be set");
    }
  }

  private CertificateRequest createCertificateRequest() {
    CertificateRequest request = new CertificateRequest();
    request.setRelyingPartyUUID(relyingPartyUUID);
    request.setRelyingPartyName(relyingPartyName);
    request.setCertificateLevel(certificateLevel);
    return request;
  }

  private X509Certificate parseX509Certificate(String certificateValue) {
    logger.debug("Parsing X509 certificate");
    String certificateString = X509Factory.BEGIN_CERT + "\n" + certificateValue + "\n" + X509Factory.END_CERT;
    try {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificateString.getBytes()));
    } catch (CertificateException e) {
      logger.error("Failed to parse X509 certificate from " + certificateString + ". Error " + e.getMessage());
      throw new TechnicalErrorException("Failed to parse X509 certificate from " + certificateString + ". Error " + e.getMessage(), e);
    }
  }
}
