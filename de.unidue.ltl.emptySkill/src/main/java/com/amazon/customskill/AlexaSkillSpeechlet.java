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
	static int wrongAnswers; // in stage 2: How many wrong answers did the user give?
	static int rightAnswers; // in stage 2: How many right answers did the user give?
	static int sentencesPlayed; // in stage 2: how many sentences did the user play?
	static int sentencesInChapter; // how many sentences does the chapter contain?
	static int currentTopic; // which vocabulary topic does the user learn?
	static String answerOption1 = "";
	static String answerOption2 = "";
	static boolean publikumUsed;
	static boolean fiftyfiftyUsed;
	static String question = "";
	static String correctAnswer = ""; //in stage 2: What is the correct answer for the game?
	static String name = "";
	static String currentVocabEnglish = ""; //current english vocab from stage 1
	static String currentVocabGerman = ""; //current german vocab from stage 1
	static int currentVocabCounter; //index of current vocab
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
	static enum CurrentTopic {Eins, Zwei, Drei};
	CurrentTopic topicRightNow;
	
	// Which game does the user play? ("complete the sentence", "this or that"...)
	static enum CurrentGame {Eins, Zwei, Drei};
	CurrentGame gameRightNow;

	// What does the user want? (he wants an explanation/ he wants to continue/ he doesn't know the answer...)
	static enum UserIntent {Ja, Nein, Answer, A, B, C, D, Eins, Zwei, Publikum, FiftyFifty, Explanation, Weiter, Idk, Solution, Name, NochEinmal, Exit, Error, Hörsaal, Etage};
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
		logger.info("We received the welcome message");
		return askUserTwoStrings(utterances.get("welcomeMessage1"), utterances.get("welcomeMessage2"), 3); //we jump to our method "askUserTwoStrings"
	}
	
	
	private void selectVocab() {
		try {
			con = DBConnection.getConnection();
			Statement stmt = con.createStatement();
			ResultSet rs = stmt
					.executeQuery("SELECT * FROM stage1 WHERE id=" + index + "");
			currentVocabEnglish = rs.getString("english");
			currentVocabGerman = rs.getString("german");
			logger.info("extracted the english vocab: "+ currentVocabEnglish + " and the german vocab: " + currentVocabGerman);
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
        case Stage2: resp = evaluateAnswer(userRequest); break;
		default: resp = tellUserAndFinish("Erkannter Text: " + userRequest);
		}   
		return resp;
	}

	private SpeechletResponse evaluateAnswer(String userRequest) {
		SpeechletResponse res = null;
		//recognizeUserIntent(userRequest); //here, we shortly jump to "recognizeUserIntent" to match the user input with expressions!
		
		switch (recState) {
		
		case Beginning:{ //if we are on the stage "Beginning":	
			
			recognizeUserIntent(userRequest);
			
			switch (ourUserIntent) {	
			
			case Ja: { //if the user wanted to say "Yes":
				logger.info("The user said that he's new on this app.");
				firstUse = FirstTime.First;
				expState = ExplanationState.On;
				res = askUserCombined(utterances.get("newhere"),5);
			}; break;	
			
			case Nein: {
				logger.info("The user said that he has used Linguax Prep before.");
				recState = RecognitionState.Stage1;
				//res = askUserResponse(utterances.get("notnewhere") + " " + utterances.get("whichtopic"));
				res = askUserCombined(utterances.get("notnewhere"),7);
			}; break;
			
			case NochEinmal:{
				logger.info("The user wants a repetition of Alexa's output.");
				res = askUserCombined(utterances.get("newhere"),5);
			}; break;
			
			case Weiter:{
				logger.info("The user understood Alexa's explanation and wants to continue.");
				expState = ExplanationState.Off;
				recState = RecognitionState.Stage1;
				res = askUserCombined(utterances.get("letsStart"),7);
			}; break;
			
			default:{
				logger.info("The user said something we didn't understand.");
				res = askUserResponse(utterances.get("didnotunderstand"));
			}
			}
		}; break;
		
		
		case Stage1:{
			
			recognizeUserIntent(userRequest);
			
			switch (ourUserIntent) {
			case Eins: {
				switch (firstUse) {
				case First: {
					logger.info("The User wants to learn topic 1");
					currentTopic = 1;
					res = askUserCombined(utterances.get("explainStage1"),5);
				}; break;
				case NotFirst: {
					currentTopic = 1;
					logger.info("Stage 1 topic " + currentTopic + " begins.");
					recState = RecognitionState.Stage1inAction;
					selectVocab();
					res = askUserThreeStrings(utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
				}; break;
				default: {
					logger.info("The user said something we didn't understand.");
					res = askUserResponse(utterances.get("didnotunderstand"));
				} //break;
				}
			}; break;
			case Weiter: {
				logger.info("Stage 1 topic " + currentTopic + " begins.");
				firstStage();
				recState = RecognitionState.Stage1inAction;
				res = askUserThreeStrings(utterances.get("letsStart"), currentVocabEnglish, currentVocabGerman, 1);
			}; break;
			case Hörsaal: {
				vocabsLearned += 1;
				firstStage();
				res = askUserFourStrings(utterances.get("thatWasCorrect"), utterances.get("letsContinue"), currentVocabEnglish, currentVocabGerman, 1);
			}; break;
			case Etage: {
				recState = RecognitionState.BetweenStages;
				res = askUserTwoStrings(utterances.get("thatWasCorrect"), utterances.get("finishedStageOne"), 2);
			}; break;
			default:{
				logger.info("The user said something we didn't understand.");
				res = askUserResponse(utterances.get("didnotunderstand"));
			}
			}
		}; break;
		
		
		case Stage1inAction:{
			logger.info("current vocab german is: " + currentVocabGerman);
			logger.info("user request is: " + userRequest);
			//userRequest = userRequest.toLowerCase(); 
			if (userRequest.equals(currentVocabGerman)) {
				String oldVocabGerman = currentVocabGerman;
				String oldVocabEnglish = currentVocabEnglish;
				firstStage();
				if (index != 5) {
					res = askUserFourStrings(oldVocabGerman, oldVocabEnglish, currentVocabEnglish, currentVocabGerman, 2);
				} else {
					recState = RecognitionState.BetweenStages;
					res = askUserTwoStrings(oldVocabGerman, oldVocabEnglish, 4);
				}
				
			} else {
				res = askUserResponse("Oh hell no");
			}
		}; break;
		
		
		case BetweenStages:{
			
			recognizeUserIntent(userRequest);
			
			switch (ourUserIntent) {
			case Eins: { // = Complete The Sentence
				logger.info("The user chooses 'Complete The Sentence'.");
				switch (firstUse) {
				case First: {
					res = askUserCombined(utterances.get("explainCTS"),5);
				}; break;
				case NotFirst: {
				}; break;
				}
			}; break;
			default:{
				logger.info("The user said something we didn't understand.");
				res = askUserResponse(utterances.get("didnotunderstand"));
			}
			}
		}; break;
		
		
		case Stage2:{
			switch (ourUserIntent) {
			case Idk: {
				logger.info("The user doesn't know the correct answer.");
			} break;
			default:{
				logger.info("The user said something we didn't understand.");
				res = askUserResponse(utterances.get("didnotunderstand"));
			}
			}
		}; break;
		
		case Ending:{
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

	
	// here is the part where our Regular Expressions (RegEx) happen:
	void recognizeUserIntent(String userRequest) {
		userRequest = userRequest.toLowerCase();
		String pattern1 = "(ich nehme )?(antwort )?(\\b[a-d]\\b)( bitte)?";
		String pattern2 = "(ich nehme )?(den )?publikumsjoker( bitte)?";
		String pattern3 = "(ich nehme )?(den )?(fiftyfifty|fÃ¼nfzigfÃ¼nfzig) joker( bitte)?";
		String pattern4 = "\\bnein\\b";
		String pattern5 = "\\bja\\b";
		String pattern6 = "\\bweiter\\b";
		String pattern7 = "\\bnoch einmal\\b";
		String pattern8 = "\\beins\\b";
		String pattern9 = "\\bhörsaal\\b"; // this is not how it should be
		String pattern10 = "\\betage\\b"; // this is not how it should be
		String pattern11 = "\\bich weiß es nicht\\b";

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
		Pattern p9 = Pattern.compile(pattern9); // this is not how it should be
		Matcher m9 = p9.matcher(userRequest); // this is not how it should be
		Pattern p10 = Pattern.compile(pattern10); // this is not how it should be
		Matcher m10 = p10.matcher(userRequest); // this is not how it should be
		Pattern p11 = Pattern.compile(pattern11);
		Matcher m11 = p11.matcher(userRequest);
		
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
			ourUserIntent = UserIntent.Hörsaal; // this is not how it should be
		} else if (m10.find()) {
			ourUserIntent = UserIntent.Etage; // this is not how it should be
		} else if (m11.find()) {
			ourUserIntent = UserIntent.Idk;
		} else {
			ourUserIntent = UserIntent.Error;
		}
		logger.info("set ourUserIntent to " + ourUserIntent);
		//after that, we jump back to the method "evaluateAnswer" (where we came from!)
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

	
	

	// (this method is not ready yet)
	// this method should happen after stage 2:
	// our user gets a ranking, how good/bad he did at the game
	void userRanking() { 
		switch (sentencesPlayed) {
		case 15: // (the number can be replaced): If the user played all sentences, he will get his ranking
			if (wrongAnswers > rightAnswers) { //if there were more wrong than right answers:
				askUserResponse(buildString(utterances.get("finished"), String.valueOf(topicRightNow), "") + " " + utterances.get("notGood") + " " + utterances.get("whatDo"));
			} else if (rightAnswers == wrongAnswers) { // if there were more right than wrong answers
				askUserResponse(buildString(utterances.get("finished"), String.valueOf(topicRightNow), "") + " " + utterances.get("okay") + " " + utterances.get("whatDo"));
			} else {
				askUserResponse(buildString(utterances.get("finished"), String.valueOf(topicRightNow), "") + " " + utterances.get("good") + " " + utterances.get("whatDo"));
			}
		default:
		}
	}
	
	// Stage 1
//	void firstStage() {
//		if (userRequest == currentVocab) {
//			if (vocabsLearned == 2) {
//				askUserResponse(utterances.get("finishedStageOne"));
//			} else {
//				currentVocab = utterances.get("vocabTwoD");
//			}
//			currentVocab += 1;
//		}
//		else if (userRequest != currentVocab) {
//			askUserResponse(utterances.get("tryAgain"));
//		} else {
//			askUserResponse(utterances.get("didnotunderstand"));
//		}
//	}
//	
	
	
	void firstStage() { //jedes Vokabelkapitel beinhaltet 10 Vokabeln
		if (index != 5) {
			index += 1;
			selectVocab();
		} else if (index == 5) {
			selectVocab();
		} else {
			logger.info("There was an error with the method 'firstStage'.");
		}
			
	}
	
	// Stage 2, Game "Complete the Sentence"
	void completeTheSentence() {
		
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
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	/**
	 * A response to the original input - the session stays alive after an ask request was send.
	 *  have a look on https://developer.amazon.com/de/docs/custom-skills/speech-synthesis-markup-language-ssml-reference.html
	 * @param text
	 * @return
	 */
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
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + " To continue, say:" + "</voice></lang>" + " Weiter." + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " Or should I repeat it again? Then say: " + "</voice></lang>" + " Noch einmal." + "</speak>");
			break;
		case 6: // after Stage 1: Choosing a game for Stage 2
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + " Choose a game for stage 2." + "</voice></lang>" + " Eins: " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Complete the sentence. Or: " + "</voice></lang>" + "Zwei: " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "This or That." + "</lang></voice></speak>");
			break;
		case 7: // "Which topic do you want to learn?..."
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text + " Which vocabulary topic do you want to learn? If you want to learn topic one, university buildings and orientation, simply say: " + "</voice></lang>" + " Eins. " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " For topic two, class, say: " + "</voice></lang>" + "Zwei." + "</speak>");
			break;
		}
		
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hey!</emphasis> Bist du noch da?</speak>");

		Reprompt rep = new Reprompt();
		rep.setOutputSpeech(repromptSpeech);

		return SpeechletResponse.newAskResponse(speech, rep);
		
	}
	
	
	private SpeechletResponse askUserTwoStrings (String text1, String text2, int n)
	{
		SsmlOutputSpeech speech = new SsmlOutputSpeech();
		
		switch (n) {
		case 1: // mixed German & English response: Continue or Repeat
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + text2 + " To continue, say:" + "</voice></lang>" + " Weiter." + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " Or should I repeat it again? Then say: " + "</voice></lang>" + " Noch einmal." + "</speak>");
			break;
		case 2: // after Stage 1: correct & completed; choosing a game for Stage 2
			speech.setSsml("<speak><audio src='soundbank://soundlibrary/ui/gameshow/amzn_ui_sfx_gameshow_neutral_response_01'/><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + " " + text2 + " " + "Choose a game for stage 2." + "</voice></lang>" + " Eins: " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Complete the sentence. Or: " + "</voice></lang>" + "Zwei: " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "This or That." + "</voice></lang></speak>");
			break;
		case 3: // Beginning sequence
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + text1 + "<audio src='soundbank://soundlibrary/voices/crowds/crowds_01'/>" + text2 + " Please answer with:" + "</voice></lang>" + " Ja:" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " for yes, and:" + "</voice></lang>" + " Nein:" + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " for no." + "</voice></lang></speak>");
			break;
		case 4: // Stage 1: correct + completed (current version)
			speech.setSsml("<speak><lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "Correct! " + "</voice></lang>" + text1 + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + " means " + text2 + ". You finished stage 1. Let's do stage 2! If you want to play game 1, Complete The Sentence, say: " + "</voice></lang>" + "Eins. " + "<lang xml:lang=\"en-US\"><voice name=\"Kendra\">" + "For game 2, This or That, say: " + "</voice></lang>" + "Zwei." + "</speak>");
		}
		
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hey!</emphasis> Bist du noch da?</speak>");

		Reprompt rep = new Reprompt();
		rep.setOutputSpeech(repromptSpeech);

		return SpeechletResponse.newAskResponse(speech, rep);
		
	}
	
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
		}
		
		SsmlOutputSpeech repromptSpeech = new SsmlOutputSpeech();
		repromptSpeech.setSsml("<speak><emphasis level=\"strong\">Hey!</emphasis> Bist du noch da?</speak>");

		Reprompt rep = new Reprompt();
		rep.setOutputSpeech(repromptSpeech);

		return SpeechletResponse.newAskResponse(speech, rep);
		
	}
	
	
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
	
	


	//could be interesting for sounds:
	// <audio src="soundbank://soundlibrary/voices/crowds/crowds_01"/>
	//trumpet when user did well in the game: <audio src="soundbank://soundlibrary/musical/amzn_sfx_trumpet_bugle_04"/>
	// "ding" that was right! <audio src="soundbank://soundlibrary/ui/gameshow/amzn_ui_sfx_gameshow_neutral_response_01"/>
	// computersound: that was wrong. <audio src="soundbank://soundlibrary/computers/beeps_tones/beeps_tones_12"/>
	
	
	
	
	
	
	
	
	
	
	


}
