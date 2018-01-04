package in.leaf.abhi.treasurehunt;

import android.Manifest;
import android.arch.persistence.room.Room;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.EditText;
import android.content.Intent;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import android.widget.Chronometer;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import in.leaf.abhi.treasurehunt.database.*;

public class Questions_Activity extends AppCompatActivity {
    static final int CAMERA_PERMISSION_REQUEST_CODE=1;
    TextView questionView,teamNoView,progressView;
    EditText codeEntery;
    Button submitB,scanB;
    ProgressBar progressBar;
    Question questions[];
    int qSet[];// This array will hold the random sequence of questions
    private int totalQuestions;
    private int availableQuestions=10;
    private int qindex=-1,teamNo;
    private String enteredCode;
    private Database db;
    private QuestionsFetcher qF;
    private QuestionsDownloader qD;
    private RandomQGenerator rqg;
    private Chronometer crm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_questions_v2);
        Bundle bundle=getIntent().getExtras();
        teamNo=bundle.getInt("teamNo"); // retrieving the team no sent by the login activity
        // Initializing all global variables here
        //-----------------------------------------------------------------------------------------------
        questionView=(TextView)findViewById(R.id.questionView);
        teamNoView=(TextView)findViewById(R.id.teamNoView);
        progressView=(TextView)findViewById(R.id.progressView);
        submitB=(Button)findViewById(R.id.submitButton);
        scanB=(Button)findViewById(R.id.scanButton);
        progressBar=(ProgressBar)findViewById(R.id.progressBar);
        codeEntery=(EditText)findViewById(R.id.codeEntery);
        db= Room.databaseBuilder(Questions_Activity.this,Database.class,"AppDatabase").build();
        qD=new QuestionsDownloader();//This will download questions from the server database;
        qF=new QuestionsFetcher();
        crm=(Chronometer)findViewById(R.id.chronometer);
        //-----------------------------------------------------------------------------------------------
        downloadQuestions(); // This function will download the questions from the server
        // and save them in the app's database

        /* Setting the team number and the progress */
        teamNoView.setText(String.valueOf("Team | "+teamNo));
        progressView.setText("0/"+String.valueOf(availableQuestions));

        rqg=new RandomQGenerator();
        // The function below will generate the random numbers
        /* the totalQuestions below are subtracted by 1 because we have to fix the
           last question for every team and the last question in the db will be that fixed question
         */
        rqg.generate(totalQuestions-1,availableQuestions);
        /* This getter function below will retrieve an array of size 'availableQuestions'
           containing the randomly generated questions
         */
        qSet=rqg.getqSet();

        /* After the random questions numbers are obtained, this function will fetch
           those particular questions from the database;
         */
        fetchAvailableQuestions();
        /* Setting the Listener for the chronometer tick */
        crm.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
            @Override
            public void onChronometerTick(Chronometer chronometer) {
                if(SystemClock.elapsedRealtime()-crm.getBase()==TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS)) {
                    Toast t=new Toast(Questions_Activity.this);
                    t.setText("Time is Up!");
                    startResultsActivity();
                }
            }
        });
        /* Setting the first question for the participant */
        setNextQuestion();
        /* Starting the timer here after setting the first question*/
        crm.start();

        System.out.println("leaf");
        submitB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    enteredCode = codeEntery.getText().toString();
                    if(checkEnteredCode()) {
                        if(qindex==availableQuestions-1) {
                            startResultsActivity();
                        }
                        else {
                            setNextQuestion();
                        }
                    }
                    else {
                        //Display warning
                    }
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        scanB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (PackageManager.PERMISSION_DENIED == ContextCompat.checkSelfPermission(Questions_Activity.this, Manifest.permission.CAMERA)) {
                    System.out.println("Camera Permission denie....\nRequesting permissions");
                    ActivityCompat.requestPermissions(Questions_Activity.this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
                }
                else {
                    IntentIntegrator iIntegrator = new IntentIntegrator(Questions_Activity.this);
                    iIntegrator.initiateScan();// After the scan is complete the onRequestPermissionsResult() function is called
                }
            }
        });


    }

    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        //here only one pemission is requested in the whole activity so there is no use for cheching the requestCode;
        // and the value of requestCode will be CAMERA_PERMISSON_REQUEST_CODE
        if(grantResults==null)
            return;
        if(grantResults[0]==PackageManager.PERMISSION_GRANTED){
            System.out.println("Permission for camera use granted");
            IntentIntegrator iIntegrator = new IntentIntegrator(Questions_Activity.this);
            iIntegrator.initiateScan();
        }

    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent intent) {
        IntentResult intentResult=IntentIntegrator.parseActivityResult(requestCode,resultCode,intent);
        if(intentResult!=null) {
            enteredCode=intentResult.getContents();
            if (enteredCode!=null&&checkEnteredCode()) {
                if(qindex==availableQuestions-1) {
                    startResultsActivity();
                }
                else {
                    setNextQuestion();
                }
            }
            else {
                //show some error dialog
                System.out.println("wrong code scanned");
            }
        }
        else {
            System.out.println("Wrong QR code scanned");
        }

    }

    private void setNextQuestion() {
        try {
            qindex++;
            System.out.println("qindex : "+qindex);
            progressBar.setProgress((qindex + 1) * 100 / availableQuestions);
            progressView.setText(String.valueOf(qindex+1)+"/"+String.valueOf(availableQuestions));
            codeEntery.setText("");
            questionView.setText(questions[qindex].question+"   "+questions[qindex].answer);
        }catch(Exception e) {
            System.out.println("Unable to set next Question");
            if(availableQuestions==0)
                questionView.setText("No questions in the database");
            questionView.setText("Unable to set next Question");
            e.printStackTrace();
        }
    }

    private void fetchAvailableQuestions() {
        try {
            /* questions is an array of type 'Question' */
            questions = qF.execute(db, qSet,totalQuestions).get();
        }catch(Exception e) {
            System.out.println("Exception during fetching questions in MainActivity");
        }
    }

    private void downloadQuestions() {
        try {
            totalQuestions=Integer.parseInt(qD.execute(db).get());
            System.out.println("total questions "+totalQuestions);
            if(availableQuestions>totalQuestions)
                availableQuestions=totalQuestions;
        }catch(Exception e) {
            questionView.setText("null "+teamNo);
            e.printStackTrace();
        }
    }

    private boolean checkEnteredCode() {
        return enteredCode.equals(questions[qindex].answer);
    }

    private void startResultsActivity() {
        crm.stop();
        final long timeTaken=SystemClock.elapsedRealtime()-crm.getBase();
        Intent i = new Intent(Questions_Activity.this, Results_Activity.class);
        i.putExtra("timeTaken",timeTaken);
        startActivity(i);
        /* initiate a new backgrround thread to save results in app database */
        BackgroundThread bgt=new BackgroundThread(new Runnable(){
            @Override
            public void run() {
                ResultsDao rD=db.getResultsDao();
                if(rD.getResults()==null)
                    rD.insertResults(new Results(teamNo,timeTaken));
                else
                    rD.updateResults(new Results(teamNo,timeTaken));
            }
        });
        bgt.execute(null);
        bgt.stop();
        Questions_Activity.this.finish();
    }
}
