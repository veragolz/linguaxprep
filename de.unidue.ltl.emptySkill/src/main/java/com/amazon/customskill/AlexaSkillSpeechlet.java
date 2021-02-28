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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	static String answerOption1 = "";
	static String answerOption2 = "";
	static String rankingString = "";
	static String thema = ""; //for the method 'selectVocab'
	static boolean publikumUsed; //can be deleted
	static boolean fiftyfiftyUsed; //can be deleted
	static String question = "";
	static String correctAnswer = ""; //in stage 2: What is the correct answer for the game?
	static String name = "";
	static String currentVocabEnglish = ""; //current english vocab from stage 1
	static String currentVocabGerman = ""; //current german vocab from stage 1
	static String currentSentenceEnglish = ""; //in stage 2
	static String currentSentenceGerman1 = ""; //in stage 2
	static String currentSentenceGerman2 = ""; //in stage 2
	static String missingWord = ""; // in stage 2
	static String regularExpression = ""; // in stage 2
	static String currentTopicString = "";
	static String updatingString = "";
	static String regExVocab = ""; //important for recognizing the user intent in Stage 1 & Stage 2
	static int topicDoneAlready = 0;
	static int knowsApplication = 0;
	static int knowsStageOne = 0;
	static int knowsGameCTS = 0;
	static int wrongAnswerCounter = 0; //Stage 2: Counts the amount of wrong answers for one game sentence
	//static int currentVocabCounter; //index of current vocab
	static int vocabsLearned; //how many vocabs did the user learn?
	

	// What's the input of the user?
	public static String userRequest;

	// In which stage are we?
	static enum RecognitionState {Beginning, Stage1, Stage1inAction, BetweenStages, Stage2, Ending};
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
	static enum UserIntent {Ja, Nein, Answer, A, B, C, D, Eins, Zwei, Drei, Vier, Publikum, FiftyFifty, Explanation, Weiter, Idk, Solution, Name, NochEinmal, Exit, Error, Hörsaal, Etage};
	UserIntent ourUserIntent;

	// What our system can say (--> utterances2.txt)
	Map<String, String> utterances;

	// Baut die Systemauesserung zusammen //not used right now
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
		publikumUsed = false;
		fiftyfiftyUsed = false;
		sum = 0;
		index = 0;
		sentencesInChapter = 0;
		sentencesPlayed = 0;
		rightAnswers = 0;
		wrongAnswers = 0;
	}

	
	//onLaunch
	@Override
	public SpeechletResponse onLaunch(SpeechletRequestEnvelope<LaunchRequest> requestEnvelope)
	{
		logger.info("Dialogue begins; we ask the user if he's new on this app");
		expState = ExplanationState.Off; //our system does not explain something right now.
		recState = RecognitionState.Beginning; //our user is in the stage "beginning"
		firstUse = FirstTime.NotFirst;
		checkIfUserKnowsAppAndStages();
		logger.info("We checked, if our user knows Stage 1.");
        SpeechletResponse resp = null;
        if (knowsApplication == 0) { // User is new on Linguax PREP
			resp = askUserTwoStrings(utterances.get("iSawThatNew"), utterances.get("readyToImprove"), 3);
        } else { // User is familiar with Linguax PREP
        	resp = askUserTwoStrings(utterances.get("iSawThatNotNew"), utterances.get("stillExplanation"), 3);
		}
		return resp;
	}
	
	
	void selectVocab() { //stage 1
		if (currentTopic == 1) {
			thema = "topic1";
		} else if (currentTopic == 2) {
			thema = "topic2";
		} else if (currentTopic == 3) {
			thema = "topic3";
		} else if (currentTopic == 4) {
			thema = "topic4";
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
			regExVocab = rs.getString("regex");
			logger.info("extracted the english vocab: "+ currentVocabEnglish + " and the german vocab: " + currentVocabGerman);
		} catch (Exception e){
			logger.info("exception happens");
			e.printStackTrace();
		}
	}
	
	private void stage2CTS() { //stage 2
		if (currentTopic == 1) {
			thema = "topic1";
		} else if (currentTopic == 2) {
			thema = "topic2";
		} else if (currentTopic == 3) {
			thema = "topic3";
		} else if (currentTopic == 4) {
			thema = "topic4";
		} else {
			logger.info("There was an error with the method 'stage2CTS'.");
			thema = "topic1"; // that's not how it's supposed to be
		}
		try {
			logger.info("index = " + index);
			con = DBConnection.getConnection();
			Statement stmt = con.createStatement();
			ResultSet rs = stmt
					.executeQuery("SELECT * FROM " + thema + " WHERE id=" + index + "");
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
					res = askUserCombined(utterances.get("newhere"),5);
				} else { // if our user isn't new on this app.
					logger.info("User still wants explanation.");
					res = askUserCombined(utterances.get("newhere"),5);
				}
			}; break;	
			
			case Nein: {
				if (knowsApplication == 0) { // if our user is new on this app.
					res = tellUserAndFinish("Alright. Maybe another time. Bye then!");
				} else { // if our user isn't new on this app.
					recState = RecognitionState.Stage1;
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
				res = askUserCombined(utterances.get("letsStart"),7);
			}; break;
			
			default:{
				logger.info("The user said something we didn't understand.");
				res = askUserResponse(utterances.get("didnotunderstand"));
			}
			}
		}; break;
		
		
		case Stage1:{ //Here, the user gets ready for Stage 1. He chooses a topic.
			
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
					selectVocab();
					res = askUserFiveStrings(String.valueOf(currentTopic), topicString, utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
					//res = askUserThreeStrings(utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
				}
			}; break;
			case Zwei: { //that's not how it's supposed to be, but it works by now
				currentTopic = 2;
				topicString = "Unterricht";
				if (knowsStageOne == 0) { // if our user isn't familiar with stage 1.
					logger.info("The User wants to learn topic " + currentTopic);
					res = askUserThreeStrings(String.valueOf(currentTopic), topicString, utterances.get("explainStage1"),6);
				} else { // if our user is already familiar with stage 1.
					logger.info("Stage 1 topic " + currentTopic + " begins.");
					recState = RecognitionState.Stage1inAction;
					selectVocab();
					res = askUserFiveStrings(String.valueOf(currentTopic), topicString, utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
					//res = askUserThreeStrings(utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
				}
			}; break;
			case Drei: { //that's not how it's supposed to be, but it works by now
				currentTopic = 3;
				topicString = "Prüfungen";
				if (knowsStageOne == 0) { // if our user isn't familiar with stage 1.
					logger.info("The User wants to learn topic " + currentTopic);
					res = askUserThreeStrings(String.valueOf(currentTopic), topicString, utterances.get("explainStage1"),6);
				} else { // if our user is already familiar with stage 1.
					logger.info("Stage 1 topic " + currentTopic + " begins.");
					recState = RecognitionState.Stage1inAction;
					selectVocab();
					res = askUserFiveStrings(String.valueOf(currentTopic), topicString, utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
					//res = askUserThreeStrings(utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
				}
			}; break;
			case Vier: { //that's not how it's supposed to be, but it works by now
				currentTopic = 4;
				topicString = "Bibliothek";
				if (knowsStageOne == 0) { // if our user isn't familiar with stage 1.
					logger.info("The User wants to learn topic " + currentTopic);
					res = askUserThreeStrings(String.valueOf(currentTopic), topicString, utterances.get("explainStage1"),6);
				} else { // if our user is already familiar with stage 1.
					logger.info("Stage 1 topic " + currentTopic + " begins.");
					recState = RecognitionState.Stage1inAction;
					selectVocab();
					res = askUserFiveStrings(String.valueOf(currentTopic), topicString, utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
					//res = askUserThreeStrings(utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
				}
			}; break;
			case Weiter: {
				logger.info("Stage 1 topic " + currentTopic + " begins.");
				setUserInfo(); // updating our database to "The User knows how Stage 1 works"
				firstStage();
				recState = RecognitionState.Stage1inAction;
				res = askUserThreeStrings(utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
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
					//logger.info(oldVocabGerman + oldVocabEnglish + currentVocabGerman + currentVocabEnglish + userRequest);
					res = askUserFourStrings(oldVocabGerman, oldVocabEnglish, currentVocabEnglish, currentVocabGerman, 2);
				} else {
					//selectVocab();
					logger.info("Wir gehen in die else Verzweigung, weil index nicht mehr ungleich 5 ist.");
					recState = RecognitionState.BetweenStages;
					logger.info("Wir setzten den Recognition State auf Between Stages.");
					setUserInfo();
					logger.info("UserInfo wird aktualisiert; User hat Topic " + currentTopic + " beendet.");
					res = askUserTwoStrings(currentVocabGerman, currentVocabEnglish, 4);
				}
			}; break;
			default: {
				logger.info("The user said something that isn't the correct answer.");
				res = askUserResponse(utterances.get("notCorrectAnswer"));
			}
			}
//			logger.info("current vocab german is: " + currentVocabGerman);
//			logger.info("user request is: " + userRequest);
//			//userRequest = userRequest.toLowerCase(); 
//			if (userRequest.equals(currentVocabGerman)) {
//				logger.info("Der user Request entspricht der current vocab german!");
//				String oldVocabGerman = currentVocabGerman;
//				String oldVocabEnglish = currentVocabEnglish;
//				if (index != 3) {
//					logger.info("index is " + index);
//					firstStage();
//					logger.info("index is now " + index);
//					//logger.info(oldVocabGerman + oldVocabEnglish + currentVocabGerman + currentVocabEnglish + userRequest);
//					res = askUserFourStrings(oldVocabGerman, oldVocabEnglish, currentVocabEnglish, currentVocabGerman, 2);
//				} else {
//					//selectVocab();
//					logger.info("Wir gehen in die else Verzweigung, weil index nicht mehr ungleich 5 ist.");
//					recState = RecognitionState.BetweenStages;
//					logger.info("Wir setzten den Recognition State auf Between Stages.");
//					setUserInfo();
//					logger.info("UserInfo wird aktualisiert; User hat Topic " + currentTopic + " beendet.");
//					res = askUserTwoStrings(currentVocabGerman, currentVocabEnglish, 4);
//				}
//				
//			} else {
//				logger.info("The user gave a wrong answer in Stage 1 with: " + currentVocabEnglish + " and " + currentVocabGerman);
//				logger.info("current vocab english is:" + currentVocabEnglish);
//				res = askUserTwoStrings(currentVocabEnglish, currentVocabGerman, 5);
//			}	
		}; break;
		
		
		case BetweenStages:{ //here, our User chooses a game for Stage 2.
			
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
					completeTheSentence();
					res = askUserThreeStrings (currentSentenceEnglish, currentSentenceGerman1, currentSentenceGerman2, 3);
				}
			}; break;
			case Weiter: {
				logger.info("The user understood and wants to start the game.");
				setUserInfo(); // updating our database to "The User knows how 'Complete the Sentence' works".
				gameRightNow = CurrentGame.Eins;
				recState = RecognitionState.Stage2;
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
							res = askUserSixStrings(missingWord, currentSentenceGerman1, currentSentenceGerman2, String.valueOf(rightAnswers), String.valueOf(wrongAnswers), rankingString, 5);
						}
					} else {
						res = askUserThreeStrings(currentSentenceEnglish, currentSentenceGerman1, currentSentenceGerman2, 5);
					}
					
				}			
			}
		}; break;
		
		
		
		
		case Ending:{ //here, our User gets his User Ranking. Alexa also gives recommendations regarding what the User might do next/ might revise.
		}; break;
		
		default :{
			if (ourUserIntent.equals(UserIntent.A) 
					|| ourUserIntent.equals(UserIntent.B)
					|| ourUserIntent.equals(UserIntent.C)
					|| ourUserIntent.equals(UserIntent.D)	
					) {
				logger.info("User answer ="+ ourUserIntent.name().toLowerCase()+ "/correct answer="+correctAnswer);
				if (ourUserIntent.name().toLowerCase().equals(correctAnswer)) {
					logger.info("User answer recognized as correct.");
					increaseSum();
					if (sum == 1000000) {
						res = tellUserAndFinish(utterances.get("correctMsg")+" "+utterances.get("congratsMsg")+" "+utterances.get("goodbyeMsg"));
					} else {
						recState = RecognitionState.Stage1;
						res = askUserResponse(utterances.get("correctMsg")+" "+utterances.get("continueMsg"));
					}
				} else {
					setfinalSum();
					res = tellUserAndFinish(utterances.get("wrongMsg")+ " "+ buildString(utterances.get("sumMsg"), String.valueOf(sum), "")  + " " + utterances.get("goodbyeMsg"));
				}
			} else {
				res = askUserResponse(utterances.get("errorAnswerMsg"));
			}
		}
		}
		return res;
	}

	
	
	
	
	
	
	private void setCurrentTopicString() {
		switch (recState) {
		case Stage1: {
			currentTopicString = "KnowsApp";
		}; break;
		case Stage1inAction: {
			currentTopicString = "KnowsStage1";
		}; break;
		case BetweenStages: {
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
			Statement stmt = con.createStatement();
			updatingString = "UPDATE UserInfo set " + currentTopicString + "=1 " + "WHERE Id=1" +"";
	        stmt.executeUpdate(updatingString);
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
					.executeQuery("SELECT * FROM UserInfo WHERE id=1" + "");
			knowsApplication = rs.getInt("KnowsApp");
			knowsStageOne = rs.getInt("KnowsStage1");
			knowsGameCTS = rs.getInt("KnowsCTS");
			con.close();
			if(knowsApplication == 1) {
				firstUse = FirstTime.NotFirst;
				logger.info("Information was Extracted from UserInfo Table and User knows App");
			} else {
				firstUse = FirstTime.First;
				logger.info("Information was Extracted from UserInfo Table and User doesn't know App");
			}
			} catch (Exception e) {
				logger.info("exception happens in 'checkUserInfo'");
				e.printStackTrace();
			}
	}
	
	
	
	
	
	
	

	private void setfinalSum() {
		if (sum <500){
			sum = 0;
		}else{
			if(sum <16000){
				sum = 500;
			}else{
				sum=16000;
			}
		}

	}

	private void increaseSum() {
		switch(sum){
		case 0: sum = 50; break;
		case 50: sum = 100; break;
		case 100: sum = 200; break;
		case 200: sum = 300; break;
		case 300: sum = 500; break;
		case 500: sum = 1000; break;
		case 1000: sum = 2000; break;
		case 2000: sum = 4000; break;
		case 4000: sum = 8000; break;
		case 8000: sum = 16000; break;
		case 16000: sum = 32000; break;
		case 32000: sum = 64000; break;
		case 64000: sum = 125000; break;
		case 125000: sum = 500000; break;
		case 500000: sum = 1000000; break;
		}
	}

	
	
	void recognizeVocabs(String userRequest) {
		userRequest = userRequest.toLowerCase();
		String pattern1 = currentVocabGerman;
		
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
		String pattern1 = "(ich nehme )?(antwort )?(\\b[a-d]\\b)( bitte)?";
		String pattern2 = "(ich nehme )?(den )?publikumsjoker( bitte)?";
		String pattern3 = "(ich nehme )?(den )?(fiftyfifty|fÃ¼nfzigfÃ¼nfzig) joker( bitte)?";
		String pattern4 = "\\bnein\\b";
		String pattern5 = "\\bja\\b";
		String pattern6 = "(weiter)|((ich )?(habe )?(verstanden|verstehen?))|(ok( ay)?)|(alles klar)";
		String pattern7 = "(noch( )?(ein)?mal)|(wiederhol(ung|en))";
		String pattern8 = "(ich )?(möchte |will )?(thema |spiel )?(eins|1)( spielen| lernen)?";
		String pattern9 = "(zwei)|2"; 
		String pattern10 = "(drei)|3";
		String pattern11 = "(vier)|4";
		String pattern12 = "((ich )?weiß (es )?nicht)|(keine ahnung)|(lösung( bitte)?)";

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
			String answer = m1.group(3);
			switch (answer) {
			case "a": ourUserIntent = UserIntent.A; break;
			case "b": ourUserIntent = UserIntent.B; break;
			case "c": ourUserIntent = UserIntent.C; break;
			case "d": ourUserIntent = UserIntent.D; break;
			}
		} else if (m2.find()) {
			ourUserIntent = UserIntent.Publikum;
		} else if (m3.find()) {
			ourUserIntent = UserIntent.FiftyFifty;
		} else if (m4.find()) {
			ourUserIntent = UserIntent.Nein;
		} else if (m5.find()) {
			ourUserIntent = UserIntent.Ja;
		} else if (m6.find()) {
			ourUserIntent = UserIntent.Weiter;
		} else if (m7.find()) { //if our user wants to repeat, we set "ourUserIntent" to "Repeat"
			ourUserIntent = UserIntent.NochEinmal;
		} else if (m8.find()) {
			ourUserIntent = UserIntent.Eins;
		} else if (m9.find()) {
			ourUserIntent = UserIntent.Zwei;
		} else if (m10.find()) {
			ourUserIntent = UserIntent.Drei;
		} else if (m11.find()) {
			ourUserIntent = UserIntent.Vier;
		} else if (m12.find()) {
			ourUserIntent = UserIntent.Idk;
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
				
			}
			}
		}
		logger.info("set ourUserIntent to " + ourUserIntent);
	}


	
	
	
	
	
	
	
	
	

	void useFiftyFiftyJoker() {
		answerOption1 = correctAnswer;
		do { int r = (int) Math.round(Math.random()*4.0);
		switch(r){
		case 1: answerOption2="a"; break;
		case 2: answerOption2="b"; break;
		case 3: answerOption2="c"; break;		
		default:answerOption2="d";
		}
		} while(answerOption2==answerOption1);
		if (correctAnswer=="d" || answerOption2 == "a"
				|| (answerOption1 == "c" && answerOption2!="d")) {
			String temp = answerOption1;
			answerOption1 = answerOption2;
			answerOption2 = temp;
		}
	}

	

	void userRanking() { //Checking how many right/wrong answers our user gave in Stage 2; creating a feedback sentence
		logger.info("The user gave " + rightAnswers + " right answers and " + wrongAnswers + " wrong answers.");
		if (wrongAnswers > rightAnswers) { //if there were more wrong than right answers:
			logger.info("not good");
			rankingString = utterances.get("notGood");
		} else if (rightAnswers == wrongAnswers) { // if there were equally distributed answers
			logger.info("okay");
			rankingString = utterances.get("okay");
		} else { //if there were more right than wrong answers
			logger.info("good");
			rankingString = utterances.get("good");
		}
	}
	
	void beginningOfStage1() {
		
	}
	
	//Stage 1: Every topic contains 10 vocabs; increasing the index number until the index number is 9 (this is the 10th vocab)
	void firstStage() { 
		if (index != 3) {
			index += 1;
			selectVocab();
		} else if (index == 3) {
			selectVocab();
			logger.info("The user reached index 9");
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
			logger.info("The user reached index 9");
		} else {
			logger.info("There was an error with the method 'completeTheSentence'.");
		}
	}
	
	
	
	void usePublikumJoker() {
		int r = (int) Math.round(Math.random()*20.0);
		if (r < 1.0) {
			answerOption1 = "a";
		} else if (r < 2.0) {
			answerOption1 = "b";
		} else if (r < 3.0) {
			answerOption1 = "c";
		} else if (r < 4.0) {
			answerOption1 = "d";
		} else {
			answerOption1 = correctAnswer;
		}
	}




	@Override
	public void onSessionEnded(SpeechletRequestEnvelope<SessionEndedRequest> requestEnvelope)
	{
		logger.info("Alexa session ends now");
	}



	/**
	 * Tell the user something - the Alexa session ends after a 'tell'
	 */
	private SpeechletResponse tellUserAndFinish(String text)
	{
		// Create the plain text output.
		PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
		speech.setText(text);

		return SpeechletResponse.newTellResponse(speech);
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	//asking the User something; this is the basic english version without any ssml effects, sounds etc.
	private SpeechletResponse askUserResponse(String text)
	{		
		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + "</voice></lang></speak>");

		// if the user does not answer after 8 seconds, the systems talks again
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hey!</emphasis> Bist du noch da?</speak>");

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
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + " Please answer with:" + "</voice></lang>" + " Ja:" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " for yes, and:" + "</voice></lang>" + " Nein:" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " for no." + "</voice></lang></speak>");
			break;
		case 2: // Right answer!
			speech.setSsml("<speak><audio src='soundbank://soundlibrary/ui/gameshow/amzn_ui_sfx_gameshow_neutral_response_01'/><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + "</voice></lang></speak>");
			break;
		case 3: // Wrong answer.
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
		case 7: // "Which topic do you want to learn?..."
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + " Which vocabulary topic do you want to learn?" + "</voice></lang>" + " Eins: Universitätsgebäude und Orientierung. Zwei: Unterricht. Drei: Prüfungen. Vier: Bibliothek. Fünf: Konversationen mit Freunden." + "</speak>");
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
		case 2: // after Stage 1: correct & completed; choosing a game for Stage 2 // not used
			speech.setSsml("<speak><audio src='soundbank://soundlibrary/ui/gameshow/amzn_ui_sfx_gameshow_neutral_response_01'/><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + " " + text2 + " " + "Choose a game for stage 2." + "</voice></lang>" + " Eins: " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Complete the sentence. Or: " + "</voice></lang>" + "Zwei: " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "This or That." + "</voice></lang></speak>");
			break;
		case 3: // Beginning sequence
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + "<audio src='soundbank://soundlibrary/voices/crowds/crowds_01'/>" + text2 + " Please answer with:" + "</voice></lang>" + " Ja:" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " for yes, and:" + "</voice></lang>" + " Nein:" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " for no." + "</voice></lang></speak>");
			break;
		case 4: // Stage 1: correct + completed (current version)
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Correct! " + "</voice></lang>" + text1 + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " means " + text2 + ". You finished stage 1. Let's do stage 2! If you want to play game 1, Complete The Sentence, say: " + "</voice></lang>" + "Eins. " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "For game 2, This or That, say: " + "</voice></lang>" + "Zwei." + "</speak>");
			break;
		case 5: // Stage 1: not correct
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "No, that was not correct. Try again. " + text1 + ": " + "</voice></lang>" + text2 + "." + "</speak>");
			break;
		case 6: // Stage 2: explain CTS
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + "<audio src='soundbank://soundlibrary/musical/amzn_sfx_electronic_beep_02'/>" + text2 + " To continue, say:" + "</voice></lang>" + " Weiter." + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " Or should I repeat it again? Then say: " + "</voice></lang>" + " Noch einmal." + "</speak>");
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
	
	//special ssml outputs: asking the User something; inserting 6 Strings
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
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "The missing phrase was: " + "</voice></lang>" + text1 + ". " + text2 + text1 + text3 + ". " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Let's continue. " + text4 + "</voice></lang>" + text5 + "<audio src='soundbank://soundlibrary/musical/amzn_sfx_electronic_beep_02'/>" + text6 + " " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Which phrase is missing?" + "</voice></lang></speak>");
			break;
		case 4: // Stage 2: CTS: User doesn't know the answer, Alexa tells correct sentence & gives ranking (END)
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "The missing phrase was: " + "</voice></lang>" + text1 + ". " + text2 + text1 + text3 + ". " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " You finished this game with " + text4 + " correct and " + text5 + " wrong answers. " + text6 + " So, what do you want to do next?" + "</voice></lang></speak>");
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
