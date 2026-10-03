package com.giangnam.chat_app.utils

/**
 * Utility for standardizing phone numbers in the Vietnamese format (+84...).
 */
object PhoneUtils {
    /**
     * Normalizes a phone number to the standard format +84xxxxxxxxx.
     * Handles:
     * - "0912345678" -> "+84912345678"
     * - "84912345678" -> "+84912345678"
     * - "+84912345678" -> "+84912345678"
     * - "912345678" -> "+84912345678"
     */
    fun normalize(phoneNumber: String): String {
        // 1. Remove all non-digit characters except the leading '+'
        val hasPlus = phoneNumber.trim().startsWith("+")
        val digits = phoneNumber.replace(Regex("[^0-9]"), "")
        
        if (digits.isEmpty()) return ""

        // 2. Handle based on the extracted digits
        return when {
            // Case: Already starts with 84 and had a plus or is long enough
            digits.startsWith("84") -> "+$digits"
            
            // Case: Starts with 0 (VN local) -> Replace 0 with +84
            digits.startsWith("0") -> "+84" + digits.substring(1)
            
            // Case: Just the number (e.g., 111111111) -> Add +84
            else -> "+84$digits"
        }
    }
}
