package it.vittorioscocca.kidbox.data.passwords.otp

data class OtpConfig(
    val secret: String,
    val period: Int = 30,
    val digits: Int = 6,
    val algorithm: String = "SHA1",
)
