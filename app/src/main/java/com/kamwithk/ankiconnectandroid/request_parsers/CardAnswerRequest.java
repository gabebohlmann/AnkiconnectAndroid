package com.kamwithk.ankiconnectandroid.request_parsers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class CardAnswerRequest {
    private final long cardId;
    private final int ease;

    public CardAnswerRequest(long cardId, int ease) {
        this.cardId = cardId;
        this.ease = ease;
    }

    public long getCardId() {
        return cardId;
    }

    public int getEase() {
        return ease;
    }

    public static CardAnswerRequest fromJson(JsonElement element) {
        JsonObject object = element.getAsJsonObject();

        return new CardAnswerRequest(
                object.get("cardId").getAsLong(),
                object.get("ease").getAsInt()
        );
    }
}