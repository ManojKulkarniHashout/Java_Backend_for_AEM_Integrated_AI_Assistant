package com.hashout.aem.aiassistant.model;

public class AskQuestionResponse {
    private String answer;

    public AskQuestionResponse() {}
    public AskQuestionResponse(String answer) {
        this.answer = answer;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
