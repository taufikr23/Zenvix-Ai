package com.documind.chat;

/** Shared application limits; provider context and daily quota still apply. */
public final class ChatLimits {
    private ChatLimits() {}
    public static final int MESSAGE_CHARACTERS=100_000;
    public static final int ATTACHMENT_CHARACTERS=500_000;
    public static final int CONTEXT_CHARACTERS=750_000;
    public static final long CONTEXT_MEDIA_BYTES=12L*1024*1024;
}
