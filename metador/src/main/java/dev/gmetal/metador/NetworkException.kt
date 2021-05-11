package dev.gmetal.metador

sealed class NetworkException : RuntimeException()
class UnknownNetworkException(val innerException: Exception? = null) : NetworkException()

object ResourceNotFoundException : NetworkException()

class ServerErrorException(val httpCode: Int) : NetworkException()

class EmptyResponseException : NetworkException()
