package com.gtavi.news;

/** Deterministic request/character ceiling for one monitoring run; no guessed token accounting. */
public final class ExtractionBudget {
    private int calls;
    private int characters;
    private int usedCalls;
    private int usedCharacters;
    public ExtractionBudget(int calls, int characters) {
        this.calls = Math.max(0,calls);
        this.characters = Math.max(0,characters);
    }
    public boolean reserve(int length) {
        if(calls==0 || length>characters) return false;
        calls--; characters-=length; usedCalls++; usedCharacters+=length;
        return true;
    }
    public int usedCalls(){return usedCalls;}
    public int usedCharacters(){return usedCharacters;}
}
