/**
    Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package com.amazon.customskill;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.amazon.customskill.AlexaSkillSpeechlet.RecognitionState;
import com.amazon.customskill.AlexaSkillSpeechlet.UserIntent;
import com.amazon.speech.json.SpeechletRequestEnvelope;
import com.amazon.speech.slu.Intent;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.speechlet.SpeechletV2;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SsmlOutputSpeech;

public class AlexaSkillSpeechlet
implements SpeechletV2
{

	static Logger logger = LoggerFactory.getLogger(AlexaSkillSpeechlet.class);

	static int index;
	static int sum;
	static int wrongAnswers; // in stage 2: How many wrong answSers did the user give?
	static int rightAnswers; // in stage 2: How many right answers did the user give?
	static int sentencesPlayed; // in stage 2: how many sentences did the user play?
	static int sentencesInChapter; // how many sentences does the chapter contain? // not used
	static int currentTopic; // which vocabulary topic does the user learn?
	static String topicString; //what is the german topic string?
	static String rankingString = "";
	static int rankingInt = 0;
	static String thema = ""; //for the method 'selectVocab'
	static String question = "";
	static String correctAnswer = ""; //in stage 2: What is the correct answer for the game?
	static String name = "";
	static String currentVocabEnglish = ""; //current english vocab from stage 1
	static String currentVocabGerman = ""; //current german vocab from stage 1
	static String currentSentenceEnglish = ""; //in stage 2
	static String currentSentenceGerman1 = ""; //in stage 2
	static String currentSentenceGerman2 = ""; //in stage 2
	static String missingWord = ""; // in stage 2
	static String regularExpression = ""; // important for recognizing the user intent in Stage 2
	static String currentTopicString = "";
	static String updatingString = ""; // used to update the UserInfo in the Database
	static String regExVocab = ""; //important for recognizing the user intent in Stage 1
	static int topicDoneAlready = 0;
	static int knowsApplication = 0;
	static int knowsStageOne = 0;
	static int knowsGameCTS = 0;
	static int wrongAnswerCounter = 0;
	static int zugriff = 0; //Stage 2: After shuffling the learning sentences: Current learning sentence
	static int [] arr = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
	static int hasAlreadyChosenTopic = 0;
	//Stage 2: Counts the amount of wrong answers for one game sentence
	//static int currentVocabCounter; //index of current vocab
	static int vocabsLearned; //how many vocabs did the user learn?
	

	// What's the input of the user?
	public static String userRequest;

	// In which stage are we?
	static enum RecognitionState {Beginning, Stage1, Stage1inAction, BetweenStages, Stage2, Ending, OnOurWayToStage1};
	RecognitionState recState;
	
	// Does our user use the app for the first time?
	static enum FirstTime {First, NotFirst};
	FirstTime firstUse;
	
	// Does our system explain something right now? (for example how the game "complete the sentence" works)
	static enum ExplanationState {On, Off};
	ExplanationState expState;
	
	// Which topic does the user learn? ("university buildings", "tests and exams"...)
	static enum CurrentTopic {Eins, Zwei, Drei, Vier};
	CurrentTopic topicRightNow;
	
	// Which game does the user play? ("complete the sentence", "this or that"...)
	static enum CurrentGame {Eins, Zwei, Drei};
	CurrentGame gameRightNow;

	// What does the user want? (he wants an explanation/ he wants to continue/ he doesn't know the answer...)
	static enum UserIntent {Ja, Nein, Answer, Eins, Zwei, Drei, Vier, Fünf, Explanation, Weiter, Idk, Name, NochEinmal, Error, StageOne, StageTwo};
	UserIntent ourUserIntent;

	// What our system can say (--> utterances2.txt)
	Map<String, String> utterances;

	// builds a system output. Not used atm, but might be in future addings
	String buildString(String msg, String replacement1, String replacement2) {
		return msg.replace("{replacement}", replacement1).replace("{replacement2}", replacement2);
	}

	Map<String, String> readSystemUtterances() {
		Map<String, String> utterances = new HashMap<String, String>(); 
		try {
			for (String line : IOUtils.readLines(this.getClass().getClassLoader().getResourceAsStream("utterances2.txt"))){
				if (line.startsWith("#")){
					continue;	
				}
				String[] parts = line.split("=");
				String key = parts[0].trim();
				String utterance = parts[1].trim();
				utterances.put(key, utterance);
			}
			logger.info("Read "  +utterances.keySet().size() + "utterances");
		} catch (IOException e) {
			logger.info("Could not read utterances: "+e.getMessage());
			System.err.println("Could not read utterances: "+e.getMessage());
		}
		return utterances;
	}
	
	
	// here, we insert the name of our sqlite databank!
	static String DBName = "linguaxprepdb.db";
	private static Connection con = null;

	
	
	
	

	
	//here, our conversation starts!
	
	//onSessionStarted //here, we only assign variable values
	@Override
	public void onSessionStarted(SpeechletRequestEnvelope<SessionStartedRequest> requestEnvelope)
	{
		logger.info("Alexa session begins");
		utterances = readSystemUtterances();
		sum = 0;
		index = 0;
		sentencesInChapter = 0;
		sentencesPlayed = 0;
		rightAnswers = 0;
		wrongAnswers = 0;
	}

	
	//onLaunch: This happens when the user activates the skill with the invocation name.
	@Override
	public SpeechletResponse onLaunch(SpeechletRequestEnvelope<LaunchRequest> requestEnvelope)
	{
		logger.info("Dialogue begins; we ask the user if he's new on this app");
		expState = ExplanationState.Off; //our system does not explain something right now.
		recState = RecognitionState.Beginning; //our user is in the stage "beginning"
		firstUse = FirstTime.NotFirst;
		checkIfUserKnowsAppAndStages();
        SpeechletResponse resp = null;
        if (knowsApplication == 0) { // User is new on Linguax PREP
			resp = askUserTwoStrings(utterances.get("iSawThatNew"), utterances.get("readyToImprove"), 3);
        } else { // User is familiar with Linguax PREP
        	resp = askUserTwoStrings(utterances.get("iSawThatNotNew"), utterances.get("stillExplanation"), 3);
		}
		return resp;
	}
	
	// Stage 1: The system chooses the current vocab from the database.
	void selectVocab() {
		if (currentTopic == 1) {
			thema = "topic1";
		} else if (currentTopic == 2) {
			thema = "topic2";
		} else if (currentTopic == 3) {
			thema = "topic3";
		} else if (currentTopic == 4) {
			thema = "topic4";
		} else if (currentTopic == 5) {
			thema = "topic5";
		} else {
			logger.info("There was an error with the method 'selectVocab'.");
			thema = "topic1"; // that's not how it's supposed to be
		}
		try {
			logger.info("index = " + index);
			con = DBConnection.getConnection();
			Statement stmt = con.createStatement();
				ResultSet rs = stmt
						.executeQuery("SELECT * FROM " + thema + " WHERE id=" + index + "");
			currentVocabEnglish = rs.getString("english");
			currentVocabGerman = rs.getString("german");
			regExVocab = rs.getString("regexstage1");
			logger.info("extracted the english vocab: "+ currentVocabEnglish + " and the german vocab: " + currentVocabGerman);
		} catch (Exception e){
			logger.info("exception happens");
			e.printStackTrace();
		}
	}
	
	private static void shuffleArray(int[] array)
	{
	    int indi;
	    Random random = new Random();
	    for (int i = array.length - 1; i > 0; i--)
	    {
	        indi = random.nextInt(i + 1);
	        if (indi != i)
	        {
	            array[indi] ^= array[i];
	            array[i] ^= array[indi];
	            array[indi] ^= array[i];
	        }
	    }
	}
	
	// Stage 2: Complete The Sentence: The system chooses the current part sentences from the database.
	private void stage2CTS() {
		if (currentTopic == 1) {
			thema = "topic1";
		} else if (currentTopic == 2) {
			thema = "topic2";
		} else if (currentTopic == 3) {
			thema = "topic3";
		} else if (currentTopic == 4) {
			thema = "topic4";
		} else if (currentTopic == 5) {
			thema = "topic5";
		} else {
			logger.info("There was an error with the method 'stage2CTS'.");
			thema = "topic1"; // that's not how it's supposed to be
		}
		try {
			logger.info("index = " + index);
			zugriff = arr[index];
			con = DBConnection.getConnection();
			Statement stmt = con.createStatement();
			ResultSet rs = stmt
					.executeQuery("SELECT * FROM " + thema + " WHERE id=" + zugriff + "");
			currentSentenceEnglish = rs.getString("englishsent");
			currentSentenceGerman1 = rs.getString("germansent1");
			currentSentenceGerman2 = rs.getString("germansent2");
			missingWord = rs.getString("missing");
			regularExpression = rs.getString("regex");
			logger.info("Regex phrase is: " + regularExpression);
			logger.info("extracted:" + currentSentenceEnglish + " - " + currentSentenceGerman1 + " - " + missingWord + " - " + currentSentenceGerman2);
		} catch (Exception e){
			logger.info("exception happens");
			e.printStackTrace();
		}
	}
	
	//onIntent //here, not much happens: after this method, we jump to "evaluateAnswer"
	@Override
	public SpeechletResponse onIntent(SpeechletRequestEnvelope<IntentRequest> requestEnvelope)
	{
		IntentRequest request = requestEnvelope.getRequest();
        Intent intent = request.getIntent();
        userRequest = intent.getSlot("anything").getValue(); //"userRequest" is what our user said!
        logger.info("Received following text: [" + userRequest + "]");
        logger.info("recState is [" + recState + "]");
        SpeechletResponse resp = null;
        switch (recState) {
        case Beginning: resp = evaluateAnswer(userRequest); break;
        case Stage1: resp = evaluateAnswer(userRequest); break; 
        case Stage1inAction: resp = evaluateAnswer(userRequest); break;
        case BetweenStages: resp = evaluateAnswer(userRequest); break;
        case Stage2: resp = evaluateAnswer(userRequest); break;
        case Ending: resp = evaluateAnswer(userRequest); break;
        case OnOurWayToStage1: resp = evaluateAnswer(userRequest); break;
		default: resp = tellUserAndFinish("Erkannter Text: " + userRequest);
		}   
		return resp;
	}

	private SpeechletResponse evaluateAnswer(String userRequest) {
		SpeechletResponse res = null;
		//recognizeUserIntent(userRequest); //here, we shortly jump to "recognizeUserIntent" to match the user input with expressions!
		
		switch (recState) {
		
		case Beginning:{ //"Beginning":	User just began to communicate with Alexa.
			
			recognizeUserIntent(userRequest);
			
			switch (ourUserIntent) {	
			
			case Ja: {
				if (knowsApplication == 0) { // if our user is new on this app.
					//res = askUserCombined(utterances.get("newhere"),5);
					res = askUserThreeStrings(utterances.get("newhere1"), utterances.get("newhere2"), utterances.get("newhere3"), 8);
				} else { // if our user isn't new on this app.
					logger.info("User still wants explanation.");
					//res = askUserCombined(utterances.get("newhere"),5);
					res = askUserThreeStrings(utterances.get("newhere1"), utterances.get("newhere2"), utterances.get("newhere3"), 8);
				}
			}; break;	
			
			case Nein: {
				if (knowsApplication == 0) { // if our user is new on this app.
					res = tellUserAndFinish(utterances.get("tschuess"));
				} else { // if our user isn't new on this app.
					recState = RecognitionState.Stage1;
					hasAlreadyChosenTopic = 0;
					res = askUserCombined(utterances.get("notnewhere"),7);
				}
			}; break;
			
			case NochEinmal:{
				logger.info("The user wants a repetition of Alexa explanation of the app.");
				res = askUserResponse(utterances.get("newhere"));
				//res = askUserCombined(utterances.get("newhere"),5);
			}; break;
			
			case Weiter:{
				logger.info("The user understood Alexa's explanation and wants to continue.");
				expState = ExplanationState.Off;
				setUserInfo(); // updating our database to "The User knows, how this app works"
				recState = RecognitionState.Stage1;
				hasAlreadyChosenTopic = 0;
				res = askUserCombined(utterances.get("letsStart"),7);
			}; break;
			
			default:{
				logger.info("The user said something we didn't understand.");
				res = askUserResponse(utterances.get("didnotunderstand"));
			}
			}
		}; break;
		
		//Here, the user gets ready for Stage 1. He chooses a topic.
		case Stage1:{ 
			
			index = 0; //resetting the index in case the user does Stage 1 more than one time in one skill cycle.
			
			recognizeUserIntent(userRequest);
			
			switch (ourUserIntent) {
			case Eins: {
				currentTopic = 1;
				topicString = "Universitätsgebäude und Orientierung";
				if (knowsStageOne == 0) { // if our user isn't familiar with stage 1.
					logger.info("The User wants to learn topic " + currentTopic + ": " + topicString);
					res = askUserThreeStrings(String.valueOf(currentTopic), topicString, utterances.get("explainStage1"),6);
				} else { // if our user is already familiar with stage 1.
					logger.info("Stage 1 topic " + currentTopic + " begins.");
					recState = RecognitionState.Stage1inAction;
					index = 0;
					selectVocab();
					res = askUserFiveStrings(String.valueOf(currentTopic), topicString, utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
				}
			}; break;
			case Zwei: { 
				currentTopic = 2;
				topicString = "Unterricht";
				if (knowsStageOne == 0) { // if our user isn't familiar with stage 1.
					logger.info("The User wants to learn topic " + currentTopic);
					res = askUserThreeStrings(String.valueOf(currentTopic), topicString, utterances.get("explainStage1"),6);
				} else { // if our user is already familiar with stage 1.
					logger.info("Stage 1 topic " + currentTopic + " begins.");
					recState = RecognitionState.Stage1inAction;
					index = 0;
					selectVocab();
					res = askUserFiveStrings(String.valueOf(currentTopic), topicString, utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
				}
			}; break;
			case Drei: { 
				currentTopic = 3;
				topicString = "Prüfungen";
				if (knowsStageOne == 0) { // if our user isn't familiar with stage 1.
					logger.info("The User wants to learn topic " + currentTopic);
					res = askUserThreeStrings(String.valueOf(currentTopic), topicString, utterances.get("explainStage1"),6);
				} else { // if our user is already familiar with stage 1.
					logger.info("Stage 1 topic " + currentTopic + " begins.");
					recState = RecognitionState.Stage1inAction;
					index = 0;
					selectVocab();
					res = askUserFiveStrings(String.valueOf(currentTopic), topicString, utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
				}
			}; break;
			case Vier: { 
				currentTopic = 4;
				topicString = "Bibliothek";
				if (knowsStageOne == 0) { // if our user isn't familiar with stage 1.
					logger.info("The User wants to learn topic " + currentTopic);
					res = askUserThreeStrings(String.valueOf(currentTopic), topicString, utterances.get("explainStage1"),6);
				} else { // if our user is already familiar with stage 1.
					logger.info("Stage 1 topic " + currentTopic + " begins.");
					recState = RecognitionState.Stage1inAction;
					index = 0;
					selectVocab();
					res = askUserFiveStrings(String.valueOf(currentTopic), topicString, utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
				}
			}; break;
			case Fünf: { 
				currentTopic = 5;
				topicString = "Konversationen mit Freunden";
				if (knowsStageOne == 0) { // if our user isn't familiar with stage 1.
					logger.info("The User wants to learn topic " + currentTopic);
					res = askUserThreeStrings(String.valueOf(currentTopic), topicString, utterances.get("explainStage1"),6);
				} else { // if our user is already familiar with stage 1.
					logger.info("Stage 1 topic " + currentTopic + " begins.");
					recState = RecognitionState.Stage1inAction;
					index = 0;
					selectVocab();
					res = askUserFiveStrings(String.valueOf(currentTopic), topicString, utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
				}
			}; break;
			case Idk: {
				logger.info("The user is unsure about the choice of vocabulary topic.");
				res = askUserCombined(utterances.get("tryOutVocabulary"),7);
			}; break;
			case Weiter: {
				logger.info("Stage 1 topic " + currentTopic + " begins.");
				firstStage();
				recState = RecognitionState.Stage1inAction;
				res = askUserThreeStrings(utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
			}; break;
			case NochEinmal: {
				if (hasAlreadyChosenTopic == 0) { //if the user wants a repetition of the provided topic choice
					res = askUserResponseGerman(utterances.get("vocabularyTopics"));
				} else { //if the user wants a repetition of the functionality of Stage 1 itself
					res = askUserResponse(utterances.get("explainStage1again"));
				}
			}; break;
			default:{
				logger.info("The user said something we didn't understand. The Intent is unknown");
				res = askUserResponse(utterances.get("didnotunderstand"));
			}
			}
		}; break;
		
		
		case Stage1inAction:{ //Here, the User plays Stage 1 with the topic of his choice.
			
			recognizeUserIntent(userRequest);
			
			switch (ourUserIntent) {
			case Answer: {
				logger.info("Der user Request entspricht der current vocab german!");
				String oldVocabGerman = currentVocabGerman;
				String oldVocabEnglish = currentVocabEnglish;
				if (index != 3) {
					logger.info("index is " + index);
					firstStage();
					logger.info("index is now " + index);
					res = askUserFourStrings(oldVocabGerman, oldVocabEnglish, currentVocabEnglish, currentVocabGerman, 2);
				} else {
					logger.info("Wir gehen in die else Verzweigung, weil index nicht mehr ungleich 10 ist.");
					recState = RecognitionState.BetweenStages;
					logger.info("Wir setzten den Recognition State auf Between Stages.");
					setUserInfo(); // updating our database to "The User knows how Stage 1 works".
					logger.info("UserInfo wird aktualisiert; User hat Topic " + currentTopic + " beendet.");
					res = askUserTwoStrings(currentVocabGerman, currentVocabEnglish, 4);
				}
			}; break;
			case Idk: { //if the user doesn't know what to answer
				res = askUserThreeStrings(utterances.get("dontGiveUp"), currentVocabEnglish, currentVocabGerman, 7);
			}; break;
			default: {
				logger.info("The user said something that isn't the correct answer.");
				res = askUserThreeStrings(utterances.get("notCorrectAnswer"), currentVocabEnglish, currentVocabGerman, 7);
			}
			}
		}; break;
		
		
		case BetweenStages:{ //here, our User chooses a game for Stage 2.
			
			// resetting each of the following variables to zero in case the user does Stage 2 more than 
			// one time in the current skill cycle.
			wrongAnswers = 0;
			rightAnswers = 0;
			wrongAnswerCounter = 0;
			index = 0;
			
			recognizeUserIntent(userRequest);
			logger.info("Our user intent in recState 'Between Stages' is: " + ourUserIntent);
			
			switch (ourUserIntent) {
			case Eins: { // = Complete The Sentence
				logger.info("The user chooses 'Complete The Sentence'.");
				if (knowsGameCTS == 0) { // if our user isn't familiar with the game 'Complete the Sentence'.
					res = askUserTwoStrings(utterances.get("explainCTS1"), utterances.get("explainCTS2"), 6);
				} else { // if our user is already familiar with the game 'Complete the Sentence'.
					gameRightNow = CurrentGame.Eins;
					recState = RecognitionState.Stage2;
					shuffleArray(arr);
					completeTheSentence();
					res = askUserThreeStrings (currentSentenceEnglish, currentSentenceGerman1, currentSentenceGerman2, 3);
				}
			}; break;
			case Idk: {
				logger.info("The user is unsure about the choice of game.");
				res = askUserCombined(utterances.get("tryOutGames"),9);
			}; break;
			case Weiter: {
				logger.info("The user understood and wants to start the game.");
				gameRightNow = CurrentGame.Eins;
				recState = RecognitionState.Stage2;
				shuffleArray(arr);
				completeTheSentence();
				res = askUserThreeStrings (currentSentenceEnglish, currentSentenceGerman1, currentSentenceGerman2, 3);
			}; break;
			case NochEinmal: {
				logger.info("The user wants a repetition of the explanation.");
				res = askUserTwoStrings(utterances.get("explainCTS1"), utterances.get("explainCTS2"), 6);
			}; break;
			default:{
				res = askUserResponse(utterances.get("didnotunderstand"));
			}
			}
		}; break;
		
		
		case Stage2:{ //here, our User plays Stage 2: Combining learned vocabulary with everyday sentences.
			recognizeUserIntent(userRequest);
			switch (ourUserIntent) {
			case Idk: { // if the user doesn't know the right answer
				logger.info("The user doesn't know the correct answer.");
				wrongAnswers += 1;
				String oldCurrentSentenceGerman1 = currentSentenceGerman1;
				String oldCurrentSentenceGerman2 = currentSentenceGerman2;
				String oldMissingWord = missingWord;
				if (index != 3) {
					completeTheSentence();
					res = askUserSixStrings(oldMissingWord, oldCurrentSentenceGerman1, oldCurrentSentenceGerman2, currentSentenceEnglish, currentSentenceGerman1, currentSentenceGerman2, 3);
				} else {
					recState = RecognitionState.Ending;
					userRanking();
					res = askUserSixStrings(oldMissingWord, oldCurrentSentenceGerman1, oldCurrentSentenceGerman2, String.valueOf(rightAnswers), String.valueOf(wrongAnswers), rankingString, 4);
				}	
			}; break;
			case Answer: { // if the user gave the correct answer
				logger.info("The user gave the correct answer.");
				rightAnswers += 1;
				String oldCurrentSentenceGerman1 = currentSentenceGerman1;
				String oldCurrentSentenceGerman2 = currentSentenceGerman2;
				String oldMissingWord = missingWord;
				if (index != 3) {
					logger.info("index was " + index);
					completeTheSentence();
					logger.info("index is now " + index);
					res = askUserSixStrings(oldCurrentSentenceGerman1, oldMissingWord, oldCurrentSentenceGerman2, currentSentenceEnglish, currentSentenceGerman1, currentSentenceGerman2, 1);
				} else {
					logger.info("Wir gehen in die else Verzweigung, weil index nicht mehr ungleich 3 ist.");
					recState = RecognitionState.Ending;
					setUserInfo(); // updating our database to "The User knows how 'Complete the Sentence' works".
					logger.info("Recognition State is 'Ending'.");
					userRanking();
					//now, our User gets his User Ranking based on the ratio between the correct & incorrect answers in Stage 2:
					res = askUserSixStrings(currentSentenceGerman1, missingWord, currentSentenceGerman2, String.valueOf(rightAnswers), String.valueOf(wrongAnswers), rankingString, 2);
				}			
			}; break;
			default: { //if the user gave a wrong answer
					wrongAnswers += 1;
					wrongAnswerCounter += 1;
					if (wrongAnswerCounter == 2) {
						if (index != 3) {
							String oldCurrentSentenceGerman1 = currentSentenceGerman1;
							String oldCurrentSentenceGerman2 = currentSentenceGerman2;
							String oldMissingWord = missingWord;
							completeTheSentence();
							res = askUserSixStrings(oldMissingWord, oldCurrentSentenceGerman1, oldCurrentSentenceGerman2, currentSentenceEnglish, currentSentenceGerman1, currentSentenceGerman2, 6);
						} else {
							recState = RecognitionState.Ending;
							userRanking();
							//Now, the User gets his User Ranking. Alexa also gives recommendations regarding what the User might do next/ might revise.
							res = askUserSixStrings(missingWord, currentSentenceGerman1, currentSentenceGerman2, String.valueOf(rightAnswers), String.valueOf(wrongAnswers), rankingString, 5);
						}
					} else {
						res = askUserThreeStrings(currentSentenceEnglish, currentSentenceGerman1, currentSentenceGerman2, 5);
					}
					
				}			
			}
		}; break;
		
		
		
		
		case Ending:{ //The user decides what he wants to do next.
			recognizeUserIntent(userRequest);
			switch (ourUserIntent) {
			case StageOne: {
				recState = RecognitionState.Stage1;
				if (rankingInt == 0) {
					res = askUserCombined(utterances.get("rankingint0"), 8);
				} else {
					res = askUserTwoStrings(utterances.get("rankingint12"), utterances.get("rankingintafter"), 7);
				}
			}; break;
			case StageTwo: {
				recState = RecognitionState.OnOurWayToStage1;
				res = askUserResponse(utterances.get("repeatstage2"));
			}; break;
			case Eins: {
				recState = RecognitionState.OnOurWayToStage1;
				res = askUserResponse(utterances.get("repeatstage2"));
			}; break;
			case Zwei: {
				recState = RecognitionState.OnOurWayToStage1;
				res = askUserResponse(utterances.get("repeatstage2"));
			}; break;
			case NochEinmal: {
				recState = RecognitionState.OnOurWayToStage1;
				res = askUserResponse(utterances.get("repeatstage2"));
			}; break;
			case Idk: {
				
			}; break;
			default: {
				logger.info("Alexa didn't understand the intent in Recognition State 'Ending'");
				res = askUserResponse(utterances.get("didnotunderstand"));
			}
			}
		}; break;
		case OnOurWayToStage1:{
			recognizeUserIntent(userRequest);
			logger.info("We are in Stage 'onourwaytostage1'");
			switch (ourUserIntent) {
			case Ja: {
				logger.info("We recognize that the user wants to do Stage 1 now.");
				recState = RecognitionState.Stage1;
				checkIfUserKnowsAppAndStages();
				if (rankingInt == 0) {
					logger.info("the user did bad.");
					res = askUserCombined(utterances.get("rankingint0"), 8);
				} else {
					logger.info("The user did okay or good.");
					res = askUserTwoStrings(utterances.get("rankingint12"), utterances.get("rankingintafter"), 7);
				}
			}; break;
			case Nein: {
				res = tellUserAndFinish(utterances.get("tschuess"));
			}; break;
			default: {
				res = askUserResponse(utterances.get("didnotunderstand"));
			}
			}
		}; break;
		
		default :{
			res = askUserResponse(utterances.get("errorAnswerMsg"));
		}
		}
		return res;
	}

	
	
	
	
	
	
	private void setCurrentTopicString() {
		switch (recState) {
		case Beginning: {
			currentTopicString = "KnowsApp";
		}; break;
		case BetweenStages: {
			currentTopicString = "KnowsStageOne";
		}; break;
		case Ending: {
			currentTopicString = "KnowsCTS";
		}; break;
		default: {
			logger.info("Something didn't work in the method setCurrentTopicString");
		}
		}
	}
	
	private void setUserInfo() { //Updating the database table 'UserInfo' regarding our user's progress.
		setCurrentTopicString();
		try {
			con = DBConnection.getConnection();
			logger.info("until 1");
			Statement stmt = con.createStatement();
			logger.info("until 2");
			updatingString = "UPDATE UserInfo SET " + currentTopicString + " = 1 " + "WHERE Id = 0";
			logger.info("until 3");
			logger.info(updatingString);
	        stmt.execute(updatingString); //hier ist ein Fehler!
	        logger.info("Set " + currentTopicString + " to 1.");
	        con.close();
		} catch(Exception e) {
			logger.info("exception happens in 'setUserInfo'");
			e.printStackTrace();
		}
	}
	
	//not used, might be not relevant
	private void checkUserInfo() { //Checking the database table 'UserInfo' regarding our user's progress.
		setCurrentTopicString();
		try {
			con = DBConnection.getConnection();
			Statement stmt = con.createStatement();
			ResultSet rs = stmt
					.executeQuery("SELECT * FROM UserInfo WHERE id=1" + "");
			topicDoneAlready = rs.getInt(currentTopicString);
			con.close();
			if(topicDoneAlready == 1) {
				firstUse = FirstTime.NotFirst;
				logger.info("Information was Extracted from UserInfo Table and the topic was done by the User");
			} else {
				firstUse = FirstTime.First;
				logger.info("Information was Extracted from UserInfo Table and the topic was NOT done by the User");
			}
			} catch (Exception e) {
				logger.info("exception happens in 'checkUserInfo'");
				e.printStackTrace();
			}
	}
	
	private void checkIfUserKnowsAppAndStages() { //Checking the database table 'UserInfo' regarding our user's progress.
		try {
			con = DBConnection.getConnection();
			Statement stmt = con.createStatement();
			ResultSet rs = stmt
					.executeQuery("SELECT * FROM UserInfo WHERE id=0" + "");
			knowsApplication = rs.getInt("KnowsApp");
			knowsStageOne = rs.getInt("KnowsStageOne");
			knowsGameCTS = rs.getInt("KnowsCTS");
			con.close();
			} catch (Exception e) {
				logger.info("exception happens in 'checkUserInfo'");
				e.printStackTrace();
			}
	}
	
	
	void recognizeVocabs(String userRequest) {
		userRequest = userRequest.toLowerCase();
		String pattern1 = regExVocab;
		
		Pattern p1 = Pattern.compile(pattern1);
		Matcher m1 = p1.matcher(userRequest);
		
		if (m1.find()) {
			logger.info("The user gave the correct answer.");
			ourUserIntent = UserIntent.Answer;
		} else {
			logger.info("We couldn't match the userRequest to 'currentVocabGerman'.");
			ourUserIntent = UserIntent.Error;
		}
	}
	
	void recognizeMissingPhrase(String userRequest) {
		userRequest = userRequest.toLowerCase();
		String pattern1 = regularExpression;
		
		Pattern p1 = Pattern.compile(pattern1);
		Matcher m1 = p1.matcher(userRequest);
		
		if (m1.find()) {
			logger.info("The user gave the correct answer.");
			ourUserIntent = UserIntent.Answer;
		} else {
			logger.info("We couldn't match the userRequest to the current Regular Expression.");
			ourUserIntent = UserIntent.Error;
		}
	}
	
	
	// here is the part where our Regular Expressions (RegEx) happen:
	void recognizeUserIntent(String userRequest) {
		userRequest = userRequest.toLowerCase();
		String pattern1 = "(phase eins)|(vokabeln?)";
		String pattern2 = "(phase zwei)|spiel(en)?";
		String pattern3 = "((ich )?weiß (es )?nicht)|(keine ahnung)|(lösung( bitte)?)";
		String pattern4 = "\\bnein\\b";
		String pattern5 = "\\bja\\b";
		String pattern6 = "(weiter)|((ich )?(habe )?(verstanden|verstehen?))|(ok( ay)?)|(alles klar)";
		String pattern7 = "(noch( )?(ein)?mal)|(wiederhol(ung|en))";
		String pattern8 = "((thema |spiel )?(eins|1))|(vervollständige(.*) satz)";
		//String pattern8 = "(ich )?(möchte |will )?(thema |spiel )?(eins|1)( spielen| lernen | machen)?|vervollständige(n)?( den)?( satz)";
		String pattern9 = "(ich )?(möchte |will )?(thema |spiel )?(zwei|2)( spielen| lernen | machen)?|dies( oder)?( das)"; 
		String pattern10 = "(ich )?(möchte |will )?(thema )?(drei|3)( spielen| lernen | machen)?";
		String pattern11 = "(ich )?(möchte |will )?(thema )?(vier|4)( spielen| lernen | machen)?";
		String pattern12 = "(ich )?(möchte |will )?(thema )?(fünf|5)( spielen| lernen | machen)?";
		//String pattern13 = "(was(.*)sagen)|hilfe|(verstehe(.*)nicht)";

		Pattern p1 = Pattern.compile(pattern1);
		Matcher m1 = p1.matcher(userRequest);
		Pattern p2 = Pattern.compile(pattern2);
		Matcher m2 = p2.matcher(userRequest);
		Pattern p3 = Pattern.compile(pattern3);
		Matcher m3 = p3.matcher(userRequest);
		Pattern p4 = Pattern.compile(pattern4);
		Matcher m4 = p4.matcher(userRequest);
		Pattern p5 = Pattern.compile(pattern5);
		Matcher m5 = p5.matcher(userRequest);
		Pattern p6 = Pattern.compile(pattern6);
		Matcher m6 = p6.matcher(userRequest);
		Pattern p7 = Pattern.compile(pattern7);
		Matcher m7 = p7.matcher(userRequest);
		Pattern p8 = Pattern.compile(pattern8);
		Matcher m8 = p8.matcher(userRequest);
		Pattern p9 = Pattern.compile(pattern9);
		Matcher m9 = p9.matcher(userRequest); 
		Pattern p10 = Pattern.compile(pattern10);
		Matcher m10 = p10.matcher(userRequest);
		Pattern p11 = Pattern.compile(pattern11);
		Matcher m11 = p11.matcher(userRequest);
		Pattern p12 = Pattern.compile(pattern12);
		Matcher m12 = p12.matcher(userRequest);
		
		if (m1.find()) {
			ourUserIntent = UserIntent.StageOne;
		} else if (m2.find()) {
			ourUserIntent = UserIntent.StageTwo;
		} else if (m3.find()) {
			ourUserIntent = UserIntent.Idk;
		} else if (m4.find()) {
			ourUserIntent = UserIntent.Nein;
		} else if (m5.find()) {
			ourUserIntent = UserIntent.Ja;
		} else if (m6.find()) {
			ourUserIntent = UserIntent.Weiter;
		} else if (m7.find()) { //if our user wants to repeat, we set "ourUserIntent" to "Repeat"
			ourUserIntent = UserIntent.NochEinmal;
		} else if (m8.find()) {
			logger.info("Wir erkennen Eins als Intent!");
			ourUserIntent = UserIntent.Eins;
		} else if (m9.find()) {
			logger.info("Wir erkennen Zwei als Intent!");
			ourUserIntent = UserIntent.Zwei;
		} else if (m10.find()) {
			ourUserIntent = UserIntent.Drei;
		} else if (m11.find()) {
			ourUserIntent = UserIntent.Vier;
		} else if (m12.find()) {
			ourUserIntent = UserIntent.Fünf;
		} else {
			switch (recState) {
			case Stage1inAction:{
				recognizeVocabs(userRequest);
			}; break;
			case Stage2:{
				recognizeMissingPhrase(userRequest);
			}; break;
			default: {
				logger.info("Something went wrong in recognizing the user intent.");
				ourUserIntent = UserIntent.Error;
			}
			}
		}
		logger.info("set ourUserIntent to " + ourUserIntent);
	}
	

	void userRanking() { //Checking how many right/wrong answers our user gave in Stage 2; creating a feedback sentence
		logger.info("The user gave " + rightAnswers + " right answers and " + wrongAnswers + " wrong answers.");
		if (wrongAnswers > rightAnswers) { //if there were more wrong than right answers:
			logger.info("not good");
			rankingInt = 0;
			rankingString = utterances.get("notGood");
		} else if (rightAnswers == wrongAnswers) { // if there were equally distributed answers
			logger.info("okay");
			rankingInt = 1;
			rankingString = utterances.get("okay");
		} else { //if there were more right than wrong answers
			logger.info("good");
			rankingInt = 2;
			rankingString = utterances.get("good");
		}
	}
	
	//Stage 1: Every topic contains 10 vocabs; increasing the index number until the index number is 9 (this is the 10th vocab)
	void firstStage() { 
		if (index != 3) {
			index += 1;
			selectVocab();
		} else if (index == 3) {
			selectVocab();
			logger.info("The user reached index 10");
		} else {
			logger.info("There was an error with the method 'firstStage'.");
		}
			
	}
	
	// Stage 2, Game: "Complete the Sentence"; every topic contains 1 sentence for each vocab; increasing the index number until it's 9 (this is the 10th sentence)
	void completeTheSentence() {
		wrongAnswerCounter = 0;
		if (index!= 3) {
			index += 1;
			stage2CTS();
		} else if (index == 3) {
			stage2CTS();
			logger.info("The user reached index 10");
		} else {
			logger.info("There was an error with the method 'completeTheSentence'.");
		}
	}
	





	@Override
	public void onSessionEnded(SpeechletRequestEnvelope<SessionEndedRequest> requestEnvelope)
	{
		logger.info("Alexa session ends now");
	}



	private SpeechletResponse tellUserAndFinish(String text)
	{
		// Create the plain text output.
		PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
		speech.setText(text);

		return SpeechletResponse.newTellResponse(speech);
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	//asking the User something; this is the basic English version without any ssml effects, sounds etc.
	private SpeechletResponse askUserResponse(String text)
	{		
		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + "</voice></lang></speak>");

		// if the user does not answer after 8 seconds, the systems talks again
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Hey! Are you still there? Please answer my question to keep our conversation going!" + "</voice></lang></speak>");

		Reprompt rep = new Reprompt();
		rep.setOutputSpeech(repromptSpeech);

		return SpeechletResponse.newAskResponse(speech, rep);
		//now, we jump to our method "onIntent"
	}

	//asking the User something; this is the basic German version without any ssml effects, sounds etc.
	private SpeechletResponse askUserResponseGerman(String text)
	{		
		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		speech.setSsml("<speak>" + text + "</speak>");

		// if the user does not answer after 8 seconds, the systems talks again
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Hey! Are you still there? Please answer my question to keep our conversation going!" + "</voice></lang></speak>");

		Reprompt rep = new Reprompt();
		rep.setOutputSpeech(repromptSpeech);

		return SpeechletResponse.newAskResponse(speech, rep);
		//now, we jump to our method "onIntent"
	}

	
	//special ssml outputs: asking the User something; inserting 1 String
	private SpeechletResponse askUserCombined (String text, int n)
	{
		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		
		switch (n) {
		case 1: // currently empty
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Hello User!" + "<audio src='soundbank://soundlibrary/voices/crowds/crowds_01'/>" + text + "</voice></lang></speak>");
			//speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + " Please answer with:" + "</voice></lang>" + " Ja:" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " for yes, and:" + "</voice></lang>" + " Nein:" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " for no." + "</voice></lang></speak>");
			break;
		case 2: // Right answer!
			speech.setSsml("<speak><audio src='soundbank://soundlibrary/ui/gameshow/amzn_ui_sfx_gameshow_neutral_response_01'/><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + "</voice></lang></speak>");
			break;
		case 3: // Wrong answer. //not used currently
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><amazon:emotion name=\"excited\" intensity=\"medium\"><audio src='soundbank://soundlibrary/computers/beeps_tones/beeps_tones_12'/>" + "That was wrong. Try again!" + "</amazon:emotion></lang></speak>");
			break;
		case 4: // mixed German & English response: Yes or No
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + " Please answer with:" + "</voice></lang>" + " Ja:" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " for yes, and:" + "</voice></lang>" + " Nein:" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " for no." + "</voice></lang></speak>");
			break;
		case 5: // mixed German & English response: Continue or Repeat?
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + " To continue, say:" + "</voice></lang>" + " Weiter." + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " If you want a repetition of my output, say: " + "</voice></lang>" + " Noch einmal." + "</speak>");
			break;
		case 6: // after Stage 1: Choosing a game for Stage 2
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + " Choose a game for stage 2." + "</voice></lang>" + " Eins: " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Complete the sentence. Or: " + "</voice></lang>" + "Zwei: " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "This or That." + "</lang></voice></speak>");
			break;
		case 7: // Stage 1: Choosing topic
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + " Which vocabulary topic do you want to learn?" + "</voice></lang>" + " Eins: Universitätsgebäude und Orientierung. Zwei: Unterricht. Drei: Prüfungen. Vier: Bibliothek. Fünf: Konversationen mit Freunden." + "</speak>");
			break;
		case 8: // After whole skill cycle: Again Stage 1: Choosing topic
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + "</voice></lang>" + " Eins: Universitätsgebäude und Orientierung. Zwei: Unterricht. Drei: Prüfungen. Vier: Bibliothek. Fünf: Konversationen mit Freunden." + "</speak>");
			break;
		case 9: // Between Stages: the user is unsure which game he wants to choose.
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + "</voice></lang>" + "Eins: Vervollständige den Satz: " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Complete The Sentence, or: " + "</voice></lang>" + "Zwei: Dies oder das: " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "This or that?" + "</voice></lang></speak>");
			break;
		}
		
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hey!</emphasis> Bist du noch da?</speak>");

		Reprompt rep = new Reprompt();
		rep.setOutputSpeech(repromptSpeech);

		return SpeechletResponse.newAskResponse(speech, rep);
		
	}
	
	//special ssml outputs: asking the User something; inserting 2 Strings
	private SpeechletResponse askUserTwoStrings (String text1, String text2, int n)
	{
		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		
		switch (n) {
		case 1: // mixed German & English response: Continue or Repeat
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + text2 + " To continue, say:" + "</voice></lang>" + " Weiter." + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " Or should I repeat it again? Then say: " + "</voice></lang>" + " Noch einmal." + "</speak>");
			break;
		case 3: // Beginning sequence
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + "<audio src='soundbank://soundlibrary/voices/crowds/crowds_01'/>" + text2 + " Please answer with:" + "</voice></lang>" + " Ja:" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " for yes, and:" + "</voice></lang>" + " Nein:" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " for no." + "</voice></lang></speak>");
			break;
		case 4: // Stage 1: correct + completed, choosing game for Stage 2
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Correct! " + "</voice></lang>" + text1 + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " means " + text2 + ". You finished stage 1. Let's do stage 2! Which game do you want to play? " + "</voice></lang>" + "Eins: Vervollständige den Satz: " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Complete The Sentence, or: " + "</voice></lang>" + "Zwei: Dies oder das: " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "This or that?" + "</voice></lang></speak>");
			break;
		case 5: // Stage 1: not correct
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "No, that was not correct. Try again. " + text1 + ": " + "</voice></lang>" + text2 + "." + "</speak>");
			break;
		case 6: // Stage 2: explain CTS
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + "<audio src='soundbank://soundlibrary/musical/amzn_sfx_electronic_beep_02'/>" + text2 + " Did you understand that or should I repeat it again?" + "</voice></lang></speak>");
			break;
		case 7: // After whole skill cycle: Again Stage 1: Choosing topic
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + "</voice></lang>" + " Phase Eins " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text2 + "</voice></lang>" + " Eins: Universitätsgebäude und Orientierung. Zwei: Unterricht. Drei: Prüfungen. Vier: Bibliothek. Fünf: Konversationen mit Freunden." + "</speak>");
			break;
		}
		
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hey!</emphasis> Bist du noch da?</speak>");

		Reprompt rep = new Reprompt();
		rep.setOutputSpeech(repromptSpeech);

		return SpeechletResponse.newAskResponse(speech, rep);
		
	}
	
	//special ssml outputs: asking the User something; inserting 3 Strings
	private SpeechletResponse askUserThreeStrings (String text1, String text2, String text3, int n)
	{
		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		
		switch (n) {
		case 1: // Stage 1: Start
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + " " + text2 + ": " + "</voice></lang>" + text3 + "." + "</speak>");
			break;
		case 2: // Stage 1: correct + completed
			speech.setSsml("<speak><audio src='soundbank://soundlibrary/ui/gameshow/amzn_ui_sfx_gameshow_neutral_response_01'/><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + " " + text2 + " " + text3 + "</voice></lang></speak>");
			break;
		case 3: // Stage 2: Complete the Sentence: Beginning
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Ok, let's play Complete The Sentence. " + text1 + " " + "</voice></lang>" + text2 + "<audio src='soundbank://soundlibrary/musical/amzn_sfx_electronic_beep_02'/>" + text3 + " " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Which phrase is missing?" + "</voice></lang></speak>");
			break;
		case 4: // Stage 2: correct + completed
			speech.setSsml("<speak><audio src=\"soundbank://soundlibrary/ui/gameshow/amzn_ui_sfx_gameshow_positive_response_02\"/><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Correct! " + "</voice></lang>" + text1 + " " + text2 + " " + text3 + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " You finished this game." + "</voice></lang></speak>");
			break;
		case 5: // Stage 2: Complete the Sentence: incorrect, try again
			speech.setSsml("<speak><audio src=\"soundbank://soundlibrary/computers/beeps_tones/beeps_tones_12\"/><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "No, that wasn't the correct answer. Try again: " + text1 + " " + "</voice></lang>" + text2 + "<audio src='soundbank://soundlibrary/musical/amzn_sfx_electronic_beep_02'/>" + text3 + " " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Which phrase is missing?" + "</voice></lang></speak>");
			break;
		case 6: // Explaining Stage 1
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "You chose topic " + text1 + ": " + "</voice></lang>" + text2 + ". " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text3 + " Did you understand that or do you want me to repeat it?" + "</voice></lang></speak>");
			break;
		case 7: // Stage 1: if the user gave a wrong answer or doesn't know what to say
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + " " + text2 + ": " + "</voice></lang>" + text3 + "." + "</speak>");
			break;
		case 8: // current mixed German & English response: Continue or Repeat?
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + "</voice></lang>" + "Phase Eins." + " " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text2 + "</voice></lang>" + "Phase Zwei" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text3 + " To continue, say something like:" + "</voice></lang>" + " Weiter." + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " If you want a repetition, say something like: " + "</voice></lang>" + " Noch einmal." + "</speak>");
			break;
		}
		
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hey!</emphasis> Bist du noch da?</speak>");

		Reprompt rep = new Reprompt();
		rep.setOutputSpeech(repromptSpeech);

		return SpeechletResponse.newAskResponse(speech, rep);
		
	}
	
	
	//special ssml outputs: asking the User something; inserting 4 Strings
	private SpeechletResponse askUserFourStrings (String text1, String text2, String text3, String text4, int n)
	{
		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		
		switch (n) {
		case 1: // Stage 1: correct + new vocab
			speech.setSsml("<speak><audio src='soundbank://soundlibrary/ui/gameshow/amzn_ui_sfx_gameshow_neutral_response_01'/><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + " " + text2 + " " + text3 + " " + "</voice></lang>" + text4 + "</speak>");
			break;
		case 2: // Stage 1: correct + repeat
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Correct! " + "</voice></lang>" + text1 + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " means " + text2 + ". Let's continue: " + text3 + "</voice></lang>" + ": " + text4 + "." + "</speak>");
			break;
		}
		
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hey!</emphasis> Bist du noch da?</speak>");

		Reprompt rep = new Reprompt();
		rep.setOutputSpeech(repromptSpeech);

		return SpeechletResponse.newAskResponse(speech, rep);
		
	}
	
	//special ssml outputs: asking the User something; inserting 5 Strings
		private SpeechletResponse askUserFiveStrings (String text1, String text2, String text3, String text4, String text5, int n)
		{
			SsmlOutputSpeech speech = new SsmlOutputSpeech();
			
			switch (n) {
			case 1: // Stage 1: no explanation, beginning
				speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "You chose topic " + text1 + ": " + "</voice></lang>" + text2 + ". " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text3 + " " + text4 + "</voice></lang>" + ": " + text5 + "</speak>");
				break;
			}
			
			SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
			repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hey!</emphasis> Bist du noch da?</speak>");

			Reprompt rep = new Reprompt();
			rep.setOutputSpeech(repromptSpeech);

			return SpeechletResponse.newAskResponse(speech, rep);
			
		}
	
	//special ssml outputs: asking the User something; inserting 6 Strings
	private SpeechletResponse askUserSixStrings (String text1, String text2, String text3, String text4, String text5, String text6, int n)
	{
		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		
		switch (n) {
		case 1: // Stage 2: correct + new sentence
			speech.setSsml("<speak><audio src=\"soundbank://soundlibrary/ui/gameshow/amzn_ui_sfx_gameshow_positive_response_02\"/><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Correct! " + "</voice></lang>" + text1 + " " + text2 + " " + text3 + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " Let's continue. " + text4 + "</voice></lang>" + text5 + "<audio src='soundbank://soundlibrary/musical/amzn_sfx_electronic_beep_02'/>" + text6 + " " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Which phrase is missing?" + "</voice></lang></speak>");
			break;
		case 2: //Stage 2: CTS: correct, completed, user ranking 
			speech.setSsml("<speak><audio src=\"soundbank://soundlibrary/ui/gameshow/amzn_ui_sfx_gameshow_positive_response_02\"/><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Correct! " + "</voice></lang>" + text1 + " " + text2 + " " + text3 + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " You finished this game with " + text4 + " correct and " + text5 + " wrong answers. " + text6 + " So, what do you want to do next?" + "</voice></lang></speak>");
			break;
		case 3: // Stage 2: CTS: User doesn't know the answer, Alexa tells correct sentence & the next game sentence
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "The missing phrase was: " + "</voice></lang>" + text1 + ". " + text2 + " " + text1 + " " + text3 + ". " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Let's continue. " + text4 +  "</voice></lang>" + text5 + "<audio src='soundbank://soundlibrary/musical/amzn_sfx_electronic_beep_02'/>" + text6 + " " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Which phrase is missing?" + "</voice></lang></speak>");
			break;
		case 4: // Stage 2: CTS: User doesn't know the answer, Alexa tells correct sentence & gives ranking (END)
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "The missing phrase was: " + "</voice></lang>" + text1 + ". " + text2 + " " + text1 + " " + text3 + ". " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " You finished this game with " + text4 + " correct and " + text5 + " wrong answers. " + text6 + " So, what do you want to do next?" + "</voice></lang></speak>");
			break;
		case 5: // Stage 2: CTS: User gives wrong answer, Alexa tells correct sentence & gives ranking (END)
			speech.setSsml("<speak><audio src=\"soundbank://soundlibrary/computers/beeps_tones/beeps_tones_12\"/><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "The missing phrase was: " + "</voice></lang>" + text1 + ". " + text2 + " " + text1 + " " + text3 + ". " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " You finished this game with " + text4 + " correct and " + text5 + " wrong answers. " + text6 + " So, what do you want to do next?" + "</voice></lang></speak>");
			break;
		case 6: // Stage 2: CTS: User gives wrong answer, Alexa tells correct sentence & the next game sentence
			speech.setSsml("<speak><audio src=\"soundbank://soundlibrary/computers/beeps_tones/beeps_tones_12\"/><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "The missing phrase was: " + "</voice></lang>" + text1 + ". " + text2 + " " + text1 + " " + text3 + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " Let's continue. " + text4 + "</voice></lang>" + text5 + "<audio src='soundbank://soundlibrary/musical/amzn_sfx_electronic_beep_02'/>" + text6 + " " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Which phrase is missing?" + "</voice></lang></speak>");
			break;
		}
		
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hey!</emphasis> Bist du noch da?</speak>");

		Reprompt rep = new Reprompt();
		rep.setOutputSpeech(repromptSpeech);

		return SpeechletResponse.newAskResponse(speech, rep);
		
	}
	


	
	//NOTES
	//
	//
	//could be interesting for sounds:
	// <audio src="soundbank://soundlibrary/voices/crowds/crowds_01"/>
	//trumpet when user did well in the game: <audio src="soundbank://soundlibrary/musical/amzn_sfx_trumpet_bugle_04"/>
	// "ding" that was right! <audio src="soundbank://soundlibrary/ui/gameshow/amzn_ui_sfx_gameshow_neutral_response_01"/>
	// computersound: that was wrong. <audio src="soundbank://soundlibrary/computers/beeps_tones/beeps_tones_12"/>
	// correct in stage 2: <audio src="soundbank://soundlibrary/ui/gameshow/amzn_ui_sfx_gameshow_positive_response_02"/>
	// stage 2: missing word is signalized by: <audio src="soundbank://soundlibrary/musical/amzn_sfx_electronic_beep_02"/>
	
	
	
	
	
	
	
	
	
	
	


}
