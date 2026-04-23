package com.fittribe.api.waitlist.dto;

public class WaitlistResponse {

    private String referralCode;
    private int position;
    private int startPosition;
    private int referralCount;
    private boolean alreadyExists;

    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public int getStartPosition() { return startPosition; }
    public void setStartPosition(int startPosition) { this.startPosition = startPosition; }
    public int getReferralCount() { return referralCount; }
    public void setReferralCount(int referralCount) { this.referralCount = referralCount; }
    public boolean isAlreadyExists() { return alreadyExists; }
    public void setAlreadyExists(boolean alreadyExists) { this.alreadyExists = alreadyExists; }
}
