package com.alinoori.share

/**
 * Wire protocol (all integers big-endian via DataOutputStream):
 *
 *  Sender -> Receiver : MAGIC(int) | senderName(UTF) | count(int) | { name(UTF) size(long) } * count
 *  Receiver -> Sender : decision(byte)  1 = accept, 0 = reject | receiverName(UTF)
 *  For each file:
 *      Sender -> Receiver : size raw bytes, then 32 bytes SHA-256 of the content
 *      Receiver -> Sender : ack(byte)  1 = hash ok, 0 = mismatch
 */
object Protocol {
    const val MAGIC = 0x414E5331 // "ANS1"
    const val PORT = 8988
    const val BUFFER = 64 * 1024
    const val MAX_FILES = 10_000
    const val ACCEPT: Int = 1
    const val REJECT: Int = 0
}
