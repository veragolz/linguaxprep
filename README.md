# linguaxprep
Our Linguax PREP Code for our PP in 2020/2021.

Zhanna Khairulina, 
Elnaz Ghofrani, 
Prudence Kahungu, 
Vera Golz

To run our skill, download our files as zip, unpack it and open Alexa Developer Console. Create a new custom skill, choose the "Hello World!" skill afterwards. Choose an invocation name. Save and build model. In the JSON Editor, drag and drop the ourIntents.json file of our project. Save and build model. As skill's service endpoint, choose HTTPS, default region, and enter the url provided by ngrok. Save Endpoints. Open Apache Tomcat and install our linguaxprep.war. Now, if you click on "test", you should be able to communicate with our skill by activating it with the chosen invocation name.

If you want to add vocabulary to Linguax PREP:

Install DB Browser (recommended for editing databases without necessarily having to use explicite SQL expressions, but you can use any database editors which enable to change SQL-databases). At "de.unidue.ltl.emptySkill/src/main/resources", you can find our "linguaxprepdb.db" database file. Open our database with your SQL-Editor. You should now be able to change, add or delete data from our created tables. If ready, save and make sure the changed database file is placed in the right folder so our program has access to it.

If you want to change our code:
