/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.KeyDetector;
import com.android.inputmethod.keyboard.Keyboard;

import java.util.Arrays;

/**
 * A place to store the currently composing word with information such as adjacent key codes as well
 */
public class WordComposer {

    public static final int NOT_A_CODE = KeyDetector.NOT_A_CODE;
    public static final int NOT_A_COORDINATE = -1;

    private static final int N = BinaryDictionary.MAX_WORD_LENGTH;

    private int[] mPrimaryKeyCodes;
    private final InputPointers mInputPointers = new InputPointers(N);
    private final StringBuilder mTypedWord;
    private CharSequence mAutoCorrection;
    private boolean mIsResumed;
    private boolean mIsBatchMode;

    // Cache these values for performance
    private int mCapsCount;
    private int mDigitsCount;
    private boolean mAutoCapitalized;
    private int mTrailingSingleQuotesCount;
    private int mCodePointSize;

    /**
     * Whether the user chose to capitalize the first char of the word.
     */
    private boolean mIsFirstCharCapitalized;

    public WordComposer() {
        mPrimaryKeyCodes = new int[N];
        mTypedWord = new StringBuilder(N);
        mAutoCorrection = null;
        mTrailingSingleQuotesCount = 0;
        mIsResumed = false;
        mIsBatchMode = false;
        refreshSize();
    }

    public WordComposer(WordComposer source) {
        mPrimaryKeyCodes = Arrays.copyOf(source.mPrimaryKeyCodes, source.mPrimaryKeyCodes.length);
        mTypedWord = new StringBuilder(source.mTypedWord);
        mInputPointers.copy(source.mInputPointers);
        mCapsCount = source.mCapsCount;
        mDigitsCount = source.mDigitsCount;
        mIsFirstCharCapitalized = source.mIsFirstCharCapitalized;
        mAutoCapitalized = source.mAutoCapitalized;
        mTrailingSingleQuotesCount = source.mTrailingSingleQuotesCount;
        mIsResumed = source.mIsResumed;
        mIsBatchMode = source.mIsBatchMode;
        refreshSize();
    }

    /**
     * Clear out the keys registered so far.
     */
    public void reset() {
        mTypedWord.setLength(0);
        mAutoCorrection = null;
        mCapsCount = 0;
        mDigitsCount = 0;
        mIsFirstCharCapitalized = false;
        mTrailingSingleQuotesCount = 0;
        mIsResumed = false;
        mIsBatchMode = false;
        refreshSize();
    }

    private final void refreshSize() {
        mCodePointSize = mTypedWord.codePointCount(0, mTypedWord.length());
    }

    /**
     * Number of keystrokes in the composing word.
     * @return the number of keystrokes
     */
    public final int size() {
        return mCodePointSize;
    }

    public final boolean isComposingWord() {
        return size() > 0;
    }

    // TODO: make sure that the index should not exceed MAX_WORD_LENGTH
    public int getCodeAt(int index) {
        if (index >= BinaryDictionary.MAX_WORD_LENGTH) {
            return -1;
        }
        return mPrimaryKeyCodes[index];
    }

    public InputPointers getInputPointers() {
        return mInputPointers;
    }

    private static boolean isFirstCharCapitalized(int index, int codePoint, boolean previous) {
        if (index == 0) return Character.isUpperCase(codePoint);
        return previous && !Character.isUpperCase(codePoint);
    }

    /**
     * Add a new keystroke, with the pressed key's code point with the touch point coordinates.
     */
    public void add(int primaryCode, int keyX, int keyY) {
        final int newIndex = size();
        mTypedWord.appendCodePoint(primaryCode);
        refreshSize();
        if (newIndex < BinaryDictionary.MAX_WORD_LENGTH) {
            mPrimaryKeyCodes[newIndex] = primaryCode >= Keyboard.CODE_SPACE
                    ? Character.toLowerCase(primaryCode) : primaryCode;
            // In the batch input mode, the {@code mInputPointers} holds batch input points and
            // shouldn't be overridden by the "typed key" coordinates
            // (See {@link #setBatchInputWord}).
            if (!mIsBatchMode) {
                // TODO: Set correct pointer id and time
                mInputPointers.addPointer(newIndex, keyX, keyY, 0, 0);
            }
        }
        mIsFirstCharCapitalized = isFirstCharCapitalized(
                newIndex, primaryCode, mIsFirstCharCapitalized);
        if (Character.isUpperCase(primaryCode)) mCapsCount++;
        if (Character.isDigit(primaryCode)) mDigitsCount++;
        if (Keyboard.CODE_SINGLE_QUOTE == primaryCode) {
            ++mTrailingSingleQuotesCount;
        } else {
            mTrailingSingleQuotesCount = 0;
        }
        mAutoCorrection = null;
    }

    public void setBatchInputPointers(InputPointers batchPointers) {
        mInputPointers.set(batchPointers);
        mIsBatchMode = true;
    }

    public void setBatchInputWord(CharSequence word) {
        reset();
        mIsBatchMode = true;
        final int length = word.length();
        for (int i = 0; i < length; i = Character.offsetByCodePoints(word, i, 1)) {
            final int codePoint = Character.codePointAt(word, i);
            // We don't want to override the batch input points that are held in mInputPointers
            // (See {@link #add(int,int,int)}).
            add(codePoint, NOT_A_COORDINATE, NOT_A_COORDINATE);
        }
    }

    /**
     * Internal method to retrieve reasonable proximity info for a character.
     */
    private void addKeyInfo(final int codePoint, final Keyboard keyboard) {
        final Key key = keyboard.getKey(codePoint);
        if (key != null) {
            final int x = key.mX + key.mWidth / 2;
            final int y = key.mY + key.mHeight / 2;
            add(codePoint, x, y);
            return;
        }
        add(codePoint, NOT_A_COORDINATE, NOT_A_COORDINATE);
    }

    /**
     * Set the currently composing word to the one passed as an argument.
     * This will register NOT_A_COORDINATE for X and Ys, and use the passed keyboard for proximity.
     */
    public void setComposingWord(final CharSequence word, final Keyboard keyboard) {
        reset();
        final int length = word.length();
        for (int i = 0; i < length; i = Character.offsetByCodePoints(word, i, 1)) {
            int codePoint = Character.codePointAt(word, i);
            addKeyInfo(codePoint, keyboard);
        }
        mIsResumed = true;
    }

    /**
     * Delete the last keystroke as a result of hitting backspace.
     */
    public void deleteLast() {
        final int size = size();
        if (size > 0) {
            // Note: mTypedWord.length() and mCodes.length differ when there are surrogate pairs
            final int stringBuilderLength = mTypedWord.length();
            if (stringBuilderLength < size) {
                throw new RuntimeException(
                        "In WordComposer: mCodes and mTypedWords have non-matching lengths");
            }
            final int lastChar = mTypedWord.codePointBefore(stringBuilderLength);
            if (Character.isSupplementaryCodePoint(lastChar)) {
                mTypedWord.delete(stringBuilderLength - 2, stringBuilderLength);
            } else {
                mTypedWord.deleteCharAt(stringBuilderLength - 1);
            }
            if (Character.isUpperCase(lastChar)) mCapsCount--;
            if (Character.isDigit(lastChar)) mDigitsCount--;
            refreshSize();
        }
        // We may have deleted the last one.
        if (0 == size()) {
            mIsFirstCharCapitalized = false;
        }
        if (mTrailingSingleQuotesCount > 0) {
            --mTrailingSingleQuotesCount;
        } else {
            int i = mTypedWord.length();
            while (i > 0) {
                i = mTypedWord.offsetByCodePoints(i, -1);
                if (Keyboard.CODE_SINGLE_QUOTE != mTypedWord.codePointAt(i)) break;
                ++mTrailingSingleQuotesCount;
            }
        }
        mAutoCorrection = null;
    }

    /**
     * Returns the word as it was typed, without any correction applied.
     * @return the word that was typed so far. Never returns null.
     */
    public String getTypedWord() {
        return mTypedWord.toString();
    }

    /**
     * Whether or not the user typed a capital letter as the first letter in the word
     * @return capitalization preference
     */
    public boolean isFirstCharCapitalized() {
        return mIsFirstCharCapitalized;
    }

    public int trailingSingleQuotesCount() {
        return mTrailingSingleQuotesCount;
    }

    /**
     * Whether or not all of the user typed chars are upper case
     * @return true if all user typed chars are upper case, false otherwise
     */
    public boolean isAllUpperCase() {
        return (mCapsCount > 0) && (mCapsCount == size());
    }

    /**
     * Returns true if more than one character is upper case, otherwise returns false.
     */
    public boolean isMostlyCaps() {
        return mCapsCount > 1;
    }

    /**
     * Returns true if we have digits in the composing word.
     */
    public boolean hasDigits() {
        return mDigitsCount > 0;
    }

    /**
     * Saves the reason why the word is capitalized - whether it was automatic or
     * due to the user hitting shift in the middle of a sentence.
     * @param auto whether it was an automatic capitalization due to start of sentence
     */
    public void setAutoCapitalized(boolean auto) {
        mAutoCapitalized = auto;
    }

    /**
     * Returns whether the word was automatically capitalized.
     * @return whether the word was automatically capitalized
     */
    public boolean isAutoCapitalized() {
        return mAutoCapitalized;
    }

    /**
     * Sets the auto-correction for this word.
     */
    public void setAutoCorrection(final CharSequence correction) {
        mAutoCorrection = correction;
    }

    /**
     * @return the auto-correction for this word, or null if none.
     */
    public CharSequence getAutoCorrectionOrNull() {
        return mAutoCorrection;
    }

    /**
     * @return whether we started composing this word by resuming suggestion on an existing string
     */
    public boolean isResumed() {
        return mIsResumed;
    }

    // `type' should be one of the LastComposedWord.COMMIT_TYPE_* constants above.
    public LastComposedWord commitWord(final int type, final String committedWord,
            final int separatorCode, final CharSequence prevWord) {
        // Note: currently, we come here whenever we commit a word. If it's a MANUAL_PICK
        // or a DECIDED_WORD we may cancel the commit later; otherwise, we should deactivate
        // the last composed word to ensure this does not happen.
        final int[] primaryKeyCodes = mPrimaryKeyCodes;
        mPrimaryKeyCodes = new int[N];
        final LastComposedWord lastComposedWord = new LastComposedWord(primaryKeyCodes,
                mInputPointers, mTypedWord.toString(), committedWord, separatorCode,
                prevWord);
        mInputPointers.reset();
        if (type != LastComposedWord.COMMIT_TYPE_DECIDED_WORD
                && type != LastComposedWord.COMMIT_TYPE_MANUAL_PICK) {
            lastComposedWord.deactivate();
        }
        mCapsCount = 0;
        mDigitsCount = 0;
        mIsBatchMode = false;
        mTypedWord.setLength(0);
        mTrailingSingleQuotesCount = 0;
        mIsFirstCharCapitalized = false;
        refreshSize();
        mAutoCorrection = null;
        mIsResumed = false;
        return lastComposedWord;
    }

    public void resumeSuggestionOnLastComposedWord(final LastComposedWord lastComposedWord) {
        mPrimaryKeyCodes = lastComposedWord.mPrimaryKeyCodes;
        mInputPointers.set(lastComposedWord.mInputPointers);
        mTypedWord.setLength(0);
        mTypedWord.append(lastComposedWord.mTypedWord);
        refreshSize();
        mAutoCorrection = null; // This will be filled by the next call to updateSuggestion.
        mIsResumed = true;
    }

    public boolean isBatchMode() {
        return mIsBatchMode;
    }
}
