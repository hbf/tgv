package com.dreizak.tgv.credentials

/**
 * Thrown when a [[com.dreizak.tgv.credentials.CredentialProvider]] cannot provide a credential.
 */
class CredentialException(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)

/**
 * Thrown when a [[com.dreizak.tgv.credentials.CredentialProvider]] cannot provide a credential because it expired.
 */
class CredentialExpiredException(msg: String, cause: Throwable = null) extends CredentialException(msg, cause)