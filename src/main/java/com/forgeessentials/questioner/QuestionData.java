package com.forgeessentials.questioner;

import com.forgeessentials.api.questioner.AnswerEnum;
import com.forgeessentials.api.questioner.RunnableAnswer;
import com.forgeessentials.util.OutputHandler;
import com.forgeessentials.commons.selections.WarpPoint;
import net.minecraft.entity.player.EntityPlayer;

public class QuestionData {
    private WarpPoint point;
    private EntityPlayer asker;
    private EntityPlayer target;
    private int waitTime;
    private int interval;
    private long startTime;
    private AnswerEnum affirmative;
    private AnswerEnum negative;

    private RunnableAnswer processAnswer;

    private String question;

    public QuestionData(WarpPoint point, EntityPlayer asker, EntityPlayer target, String question, RunnableAnswer runnable, AnswerEnum affirmative,
            AnswerEnum negative)
    {
        this.point = point;
        this.asker = asker;
        this.target = target;
        this.question = question;
        this.affirmative = affirmative;
        this.negative = negative;
        startTime = System.currentTimeMillis();
        processAnswer = runnable;
        waitTime = QuestionCenter.defaultTime;
        interval = QuestionCenter.defaultInterval;
    }

    public void setWaitTime(int seconds)
    {
        waitTime = seconds;
    }

    public void setInterval(int seconds)
    {
        interval = seconds;
    }

    public void count()
    {
        if ((System.currentTimeMillis() - startTime) / 1000L > interval)
        {
            doQuestion();
        }

        if ((System.currentTimeMillis() - startTime) / 1000L > waitTime)
        {
            QuestionCenter.abort(this);
        }
    }

    public void doAnswer(boolean affirmative)
    {
        processAnswer.setAnswer(affirmative);
        processAnswer.run();
        QuestionCenter.questionDone(this);
    }

    public void doQuestion()
    {
        OutputHandler.sendMessage(target, question);
    }

    public EntityPlayer getAsker()
    {
        return asker;
    }

    public EntityPlayer getTarget()
    {
        return target;
    }

    public AnswerEnum getAffirmative()
    {
        return affirmative;
    }

    public AnswerEnum getNegative()
    {
        return negative;
    }
}
