package com.kamwithk.ankiconnectandroid.ankidroid_api;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.ichi2.anki.FlashCardsContract;
import com.kamwithk.ankiconnectandroid.request_parsers.CardAnswerRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CardAPI {

    private final ContentResolver resolver;
    private final DeckAPI deckAPI;
    private final NoteAPI noteAPI;

    /*
     * Direct /cards support was added in AnkiDroid 2.24.
     *
     * Construct the URI manually so AnkiconnectAndroid can continue compiling
     * against its existing AnkiDroid API dependency.
     */
    private static final Uri CARDS_URI =
            Uri.withAppendedPath(FlashCardsContract.AUTHORITY_URI, "cards");

    private static final String CARD_ID = "_id";
    private static final String NOTE_ID = "note_id";
    private static final String CARD_ORD = "ord";
    private static final String DECK_ID = "deck_id";
    private static final String QUESTION = "question";
    private static final String ANSWER = "answer";
    private static final String INTERVAL = "interval";
    private static final String TYPE = "type";
    private static final String QUEUE = "queue";
    private static final String DUE = "due";
    private static final String REPS = "reps";
    private static final String LAPSES = "lapses";
    private static final String LEFT = "left";

    private static final String[] CARD_INFO_PROJECTION = {
            CARD_ID,
            NOTE_ID,
            CARD_ORD,
            DECK_ID,
            QUESTION,
            ANSWER,
            INTERVAL,
            TYPE,
            QUEUE,
            DUE,
            REPS,
            LAPSES,
            LEFT
    };

    private static final String[] CARD_REFERENCE_PROJECTION = {
            CARD_ID,
            NOTE_ID,
            CARD_ORD
    };

    public CardAPI(
            Context context,
            DeckAPI deckAPI,
            NoteAPI noteAPI
    ) {
        this.resolver = context.getContentResolver();
        this.deckAPI = deckAPI;
        this.noteAPI = noteAPI;
    }

    public ArrayList<Long> findCards(String query) throws Exception {
        ArrayList<Long> cardIds = new ArrayList<>();

        Cursor cursor;

        try {
            cursor = resolver.query(
                    CARDS_URI,
                    new String[]{CARD_ID},
                    query,
                    null,
                    null
            );
        } catch (IllegalArgumentException e) {
            throw new Exception(
                    "findCards requires AnkiDroid 2.24 or newer",
                    e
            );
        }

        if (cursor == null) {
            return cardIds;
        }

        try (cursor) {
            int idIndex = cursor.getColumnIndexOrThrow(CARD_ID);

            while (cursor.moveToNext()) {
                cardIds.add(cursor.getLong(idIndex));
            }
        }

        return cardIds;
    }

    public List<CardInfo> cardsInfo(ArrayList<Long> cardIds) throws Exception {
        List<CardInfo> result = new ArrayList<>();

        if (cardIds.isEmpty()) {
            return result;
        }

        String query = "cid:" + TextUtils.join(",", cardIds);

        Cursor cursor;

        try {
            cursor = resolver.query(
                    CARDS_URI,
                    CARD_INFO_PROJECTION,
                    query,
                    null,
                    null
            );
        } catch (IllegalArgumentException e) {
            throw new Exception(
                    "cardsInfo requires AnkiDroid 2.24 or newer",
                    e
            );
        }

        if (cursor == null) {
            return result;
        }

        Map<Long, RawCard> cards = new HashMap<>();
        Set<Long> noteIds = new LinkedHashSet<>();

        try (cursor) {
            while (cursor.moveToNext()) {
                RawCard card = new RawCard(
                        cursor.getLong(cursor.getColumnIndexOrThrow(CARD_ID)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(NOTE_ID)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(CARD_ORD)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(DECK_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(QUESTION)),
                        cursor.getString(cursor.getColumnIndexOrThrow(ANSWER)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(INTERVAL)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(TYPE)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(QUEUE)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(DUE)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(REPS)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(LAPSES)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(LEFT))
                );

                cards.put(card.cardId, card);
                noteIds.add(card.noteId);
            }
        }

        Map<Long, NoteAPI.NoteInfo> notes = new HashMap<>();

        if (!noteIds.isEmpty()) {
            ArrayList<Long> ids = new ArrayList<>(noteIds);

            List<NoteAPI.NoteInfo> noteInfoList =
                    noteAPI.notesInfo(ids);

            if (noteInfoList != null) {
                for (NoteAPI.NoteInfo info : noteInfoList) {
                    notes.put(info.getNoteId(), info);
                }
            }
        }

        Map<Long, String> deckNames = new HashMap<>();

        for (Map.Entry<String, Long> deck :
                deckAPI.deckNamesAndIds().entrySet()) {

            deckNames.put(deck.getValue(), deck.getKey());
        }

        /*
         * Return in the same order as the card IDs in the request.
         * Missing card IDs are omitted, matching AnkiConnect behavior.
         */
        for (Long cardId : cardIds) {
            RawCard card = cards.get(cardId);

            if (card == null) {
                continue;
            }

            NoteAPI.NoteInfo note = notes.get(card.noteId);

            result.add(new CardInfo(
                    card.answer,
                    card.question,
                    deckNames.get(card.deckId),
                    note == null ? null : note.getModelName(),
                    note == null ? null : note.getFields(),
                    card.cardId,
                    card.interval,
                    card.noteId,
                    card.ord,
                    card.type,
                    card.queue,
                    card.due,
                    card.reps,
                    card.lapses,
                    card.left
            ));
        }

        return result;
    }

    public List<Boolean> answerCards(
            ArrayList<CardAnswerRequest> answers
    ) throws Exception {

        List<Boolean> result = new ArrayList<>();

        if (answers.isEmpty()) {
            return result;
        }

        ArrayList<Long> ids = new ArrayList<>();

        for (CardAnswerRequest answer : answers) {
            if (answer.getEase() < 1 || answer.getEase() > 4) {
                throw new Exception("ease must be between 1 and 4");
            }

            ids.add(answer.getCardId());
        }

        String query = "cid:" + TextUtils.join(",", ids);

        Cursor cursor;

        try {
            cursor = resolver.query(
                    CARDS_URI,
                    CARD_REFERENCE_PROJECTION,
                    query,
                    null,
                    null
            );
        } catch (IllegalArgumentException e) {
            throw new Exception(
                    "answerCards requires AnkiDroid 2.24 or newer",
                    e
            );
        }

        Map<Long, CardReference> references = new HashMap<>();

        if (cursor != null) {
            try (cursor) {
                while (cursor.moveToNext()) {
                    long cardId =
                            cursor.getLong(
                                    cursor.getColumnIndexOrThrow(CARD_ID)
                            );

                    references.put(
                            cardId,
                            new CardReference(
                                    cursor.getLong(
                                            cursor.getColumnIndexOrThrow(NOTE_ID)
                                    ),
                                    cursor.getInt(
                                            cursor.getColumnIndexOrThrow(CARD_ORD)
                                    )
                            )
                    );
                }
            }
        }

        for (CardAnswerRequest answer : answers) {
            CardReference reference =
                    references.get(answer.getCardId());

            if (reference == null) {
                result.add(false);
                continue;
            }

            ContentValues values = new ContentValues();

            values.put(
                    FlashCardsContract.ReviewInfo.NOTE_ID,
                    reference.noteId
            );

            values.put(
                    FlashCardsContract.ReviewInfo.CARD_ORD,
                    reference.ord
            );

            values.put(
                    FlashCardsContract.ReviewInfo.EASE,
                    answer.getEase()
            );

            /*
             * AnkiConnect's answerCards API has no timeTaken parameter.
             */
            values.put(
                    FlashCardsContract.ReviewInfo.TIME_TAKEN,
                    0
            );

            int updated = resolver.update(
                    FlashCardsContract.ReviewInfo.CONTENT_URI,
                    values,
                    null,
                    null
            );

            result.add(updated > 0);
        }

        return result;
    }

    private static class CardReference {
        private final long noteId;
        private final int ord;

        private CardReference(long noteId, int ord) {
            this.noteId = noteId;
            this.ord = ord;
        }
    }

    private static class RawCard {
        private final long cardId;
        private final long noteId;
        private final int ord;
        private final long deckId;
        private final String question;
        private final String answer;
        private final int interval;
        private final int type;
        private final int queue;
        private final long due;
        private final int reps;
        private final int lapses;
        private final int left;

        private RawCard(
                long cardId,
                long noteId,
                int ord,
                long deckId,
                String question,
                String answer,
                int interval,
                int type,
                int queue,
                long due,
                int reps,
                int lapses,
                int left
        ) {
            this.cardId = cardId;
            this.noteId = noteId;
            this.ord = ord;
            this.deckId = deckId;
            this.question = question;
            this.answer = answer;
            this.interval = interval;
            this.type = type;
            this.queue = queue;
            this.due = due;
            this.reps = reps;
            this.lapses = lapses;
            this.left = left;
        }
    }

    public static class CardInfo {
        private final String answer;
        private final String question;
        private final String deckName;
        private final String modelName;
        private final Map<String, NoteAPI.NoteInfoField> fields;
        private final long cardId;
        private final int interval;
        private final long note;
        private final int ord;
        private final int type;
        private final int queue;
        private final long due;
        private final int reps;
        private final int lapses;
        private final int left;

        private CardInfo(
                String answer,
                String question,
                String deckName,
                String modelName,
                Map<String, NoteAPI.NoteInfoField> fields,
                long cardId,
                int interval,
                long note,
                int ord,
                int type,
                int queue,
                long due,
                int reps,
                int lapses,
                int left
        ) {
            this.answer = answer;
            this.question = question;
            this.deckName = deckName;
            this.modelName = modelName;
            this.fields = fields;
            this.cardId = cardId;
            this.interval = interval;
            this.note = note;
            this.ord = ord;
            this.type = type;
            this.queue = queue;
            this.due = due;
            this.reps = reps;
            this.lapses = lapses;
            this.left = left;
        }
    }

    private static final Uri GRADE_NOW_URI =
            Uri.withAppendedPath(
                    FlashCardsContract.AUTHORITY_URI,
                    "grade_now"
            );
    public boolean gradeNow(
            ArrayList<Long> cardIds,
            int ease
    ) throws Exception {

        if (cardIds == null || cardIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one card ID is required"
            );
        }

        if (ease < 1 || ease > 4) {
            throw new IllegalArgumentException(
                    "ease must be between 1 and 4"
            );
        }

        ContentValues values = new ContentValues();

        values.put(
                "card_ids",
                TextUtils.join(",", cardIds)
        );

        values.put(
                "ease",
                ease
        );

        int updated = resolver.update(
                GRADE_NOW_URI,
                values,
                null,
                null
        );

        return updated == cardIds.size();
    }
}