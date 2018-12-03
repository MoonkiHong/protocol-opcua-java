/*
 * Copyright (c) 2016 Kevin Herron
 *
 * All rights reserved. This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v1.0 which accompany
 * this distribution.
 *
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html and the
 * Eclipse Distribution License is available at http://www.eclipse.org/org/documents/edl-v10.html.
 */

package org.edge.protocol.opcua.session.auth;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.application.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.util.CertificateValidationUtil;
import com.google.common.collect.Sets;

public class TestCertificateValidator implements CertificateValidator {

  private final Set<X509Certificate> trustedCertificates = Sets.newConcurrentHashSet();

  /**
   * Constructor of TestCertificateManager class
   * @param certificate X509 certificate file
   */
  public TestCertificateValidator(X509Certificate certificate) {
    trustedCertificates.add(certificate);
  }

  /**
   * Constructor of TestCertificateManager class
   * @param certificates X509 certificate files
   */
  public TestCertificateValidator(X509Certificate... certificates) {
    Collections.addAll(trustedCertificates, certificates);
  }

  /**
   * overriding methods from CertificateManager
   * @param certificate X509 certificate file
   */
  @Override
  public void validate(X509Certificate certificate) throws UaException {
    CertificateValidationUtil.validateCertificateValidity(certificate);
  }

  /**
   * overriding methods from CertificateManager
   * @param certificate X509 certificate file
   * @param chain certificate chain
   */
  @Override
  public void verifyTrustChain(X509Certificate certificate, List<X509Certificate> chain)
      throws UaException {
    CertificateValidationUtil.validateTrustChain(certificate, chain, trustedCertificates,
        Sets.<X509Certificate>newHashSet());
  }

}
