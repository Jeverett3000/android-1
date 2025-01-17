BUILD_STEP = ''

// Below values will be read from MR description and are used
// decide SDK versions
SDK_BRANCH = 'develop'
MEGACHAT_BRANCH = 'develop'
SDK_COMMIT = ""
MEGACHAT_COMMIT = ""
SDK_TAG = ""
MEGACHAT_TAG = ""

// flag to decide whether we do clean before build SDK.
// Possible values: yes|no
REBUILD_SDK = "no"


pipeline {
    agent { label 'mac-jenkins-slave' }
    options {
        // Stop the build early in case of compile or test failures
        skipStagesAfterUnstable()
        buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '1'))
        timeout(time: 1, unit: 'HOURS')
        gitLabConnection('GitLabConnection')
    }
    environment {
        LC_ALL = 'en_US.UTF-8'
        LANG = 'en_US.UTF-8'

        NDK_ROOT = '/opt/buildtools/android-sdk/ndk/21.3.6528147'
        JAVA_HOME = '/opt/buildtools/zulu11.52.13-ca-jdk11.0.13-macosx'
        ANDROID_HOME = '/opt/buildtools/android-sdk'

        PATH = "/opt/buildtools/android-sdk/cmake/3.10.2.4988404/bin:/Applications/MEGAcmd.app/Contents/MacOS:/opt/buildtools/zulu11.52.13-ca-jdk11.0.13-macosx/bin:/opt/brew/bin:/opt/brew/opt/gnu-sed/libexec/gnubin:/opt/brew/opt/gnu-tar/libexec/gnubin:/opt/buildtools/android-sdk/platform-tools:/opt/buildtools/android-sdk/build-tools/31.0.0:$PATH"

        CONSOLE_LOG_FILE = 'console.txt'

        BUILD_LIB_DOWNLOAD_FOLDER = '${WORKSPACE}/mega_build_download'

        GOOGLE_MAP_API_URL = 'https://mega.nz/#!1tcl3CrL!i23zkmx7ibnYy34HQdsOOFAPOqQuTo1-2iZ5qFlU7-k'
        GOOGLE_MAP_API_FILE = 'default_google_maps_api.zip'
        GOOGLE_MAP_API_UNZIPPED = 'default_google_map_api_unzipped'

        APK_VERSION_NAME_FOR_CD = "_${new Date().format('MMddHHmm')}"
        RELEASE_NOTES_FOR_CD = "Triggered by: $gitlabUserName" +
                "\nTrigger Reason: ${getTriggerReason()}" +
                "\nBranch: $gitlabSourceBranch " +
                "\nLast 5 git commits:\n${sh(script: "git log --pretty=format:\"(%h,%an)%x09%s\" -5", returnStdout: true).trim()}"
        TESTERS_FOR_CD = getTesterList()

        // only build one architecture for SDK, to save build time. skipping "x86 armeabi-v7a x86_64"
        BUILD_ARCHS = "arm64-v8a"
    }
    post {
        failure {
            script {
                // download Jenkins console log to a file
                withCredentials([usernameColonPassword(credentialsId: 'Jenkins-Login', variable: 'CREDENTIALS')]) {
                    sh 'curl -u $CREDENTIALS ${BUILD_URL}/consoleText -o console.txt'
                }

                if (hasGitLabMergeRequest()) {
                    // if build is triggered by MR, send result to MR.
                    withCredentials([usernamePassword(credentialsId: 'Gitlab-Access-Token', usernameVariable: 'USERNAME', passwordVariable: 'TOKEN')]) {
                        // upload Jenkins console log to GitLab and get download link
                        final String response = sh(script: 'curl -s --request POST --header PRIVATE-TOKEN:$TOKEN --form file=@console.txt https://code.developers.mega.co.nz/api/v4/projects/199/uploads', returnStdout: true).trim()
                        def json = new groovy.json.JsonSlurperClassic().parseText(response)

                        String message = failureMessage("<br/>") +
                                "<br/>Build Log:\t${json.markdown}"
                        sendMRComment(message)
                    }
                } else {
                    // if build is triggered by PUSH, send result only to Slack
                    withCredentials([usernameColonPassword(credentialsId: 'Jenkins-Login', variable: 'CREDENTIALS')]) {
                        slackSend color: 'danger', message: failureMessage("\n")
                        slackUploadFile filePath: 'console.txt', initialComment: 'Jenkins Log'
                    }
                }
            }
        }
        success {
            script {
                slackSend color: "good", message: successMessage("\n")
                sendMRComment(successMessage("<br/>"))
            }
        }
    }
    stages {
        stage('Preparation') {
            steps {
                script {
                    BUILD_STEP = 'Preparation'
                    checkSDKVersion()
                    REBUILD_SDK = getValueInMRDescriptionBy("REBUILD_SDK")
                }
                gitlabCommitStatus(name: 'Preparation') {
                    sh("rm -fv ${CONSOLE_LOG_FILE}")
                    sh('set')
                }
            }
        }
        stage('Fetch SDK Submodules') {
            steps {
                script {
                    BUILD_STEP = 'Fetch SDK Submodules'
                }
                gitlabCommitStatus(name: 'Fetch SDK Submodules') {
                    withCredentials([gitUsernamePassword(credentialsId: 'Gitlab-Access-Token', gitToolName: 'Default')]) {
                        script {
                            sh '''
                            cd ${WORKSPACE}
                            git config --file=.gitmodules submodule.\"app/src/main/jni/mega/sdk\".url https://code.developers.mega.co.nz/sdk/sdk.git
                            git config --file=.gitmodules submodule.\"app/src/main/jni/mega/sdk\".branch develop
                            git config --file=.gitmodules submodule.\"app/src/main/jni/megachat/sdk\".url https://code.developers.mega.co.nz/megachat/MEGAchat.git
                            git config --file=.gitmodules submodule.\"app/src/main/jni/megachat/sdk\".branch develop
                            git submodule sync
                            git submodule update --init --recursive --remote 
                            cd app/src/main/jni/mega/sdk
                            git fetch
                            cd ../../megachat/sdk
                            git fetch
                            cd ${WORKSPACE}
                        '''
                        }
                    }
                }
            }
        }
        stage('Select SDK Version') {
            steps {
                script {
                    BUILD_STEP = 'Select SDK Version'
                }
                gitlabCommitStatus(name: 'Fetch SDK Submodules') {
                    withCredentials([gitUsernamePassword(credentialsId: 'Gitlab-Access-Token', gitToolName: 'Default')]) {
                        script {
                            if (isDefined(SDK_COMMIT)) {
                                checkoutSdkByCommit(SDK_COMMIT)
                            } else if (isDefined(SDK_TAG)) {
                                checkoutSdkByTag(SDK_TAG)
                            } else {
                                checkoutSdkByBranch(SDK_BRANCH)
                            }

                            if (isDefined(MEGACHAT_COMMIT)) {
                                checkoutMegaChatSdkByCommit(MEGACHAT_COMMIT)
                            } else if (isDefined(MEGACHAT_TAG)) {
                                checkoutMegaChatSdkByTag(MEGACHAT_TAG)
                            } else {
                                checkoutMegaChatSdkByBranch(MEGACHAT_BRANCH)
                            }
                        }
                    }
                }
            }
        }

        stage('Download Dependency Lib for SDK') {
            steps {
                script {
                    BUILD_STEP = 'Download Dependency Lib for SDK'
                }
                gitlabCommitStatus(name: 'Download Dependency Lib for SDK') {
                    sh """

                        cd "${WORKSPACE}/jenkinsfile/"
                        bash download_webrtc.sh

                        mkdir -p "${BUILD_LIB_DOWNLOAD_FOLDER}"
                        cd "${BUILD_LIB_DOWNLOAD_FOLDER}"
                        pwd
                        ls -lh

                        ## check default Google API
                        if test -f "${BUILD_LIB_DOWNLOAD_FOLDER}/${GOOGLE_MAP_API_FILE}"; then
                            echo "${GOOGLE_MAP_API_FILE} already downloaded. Skip downloading."
                        else
                            echo "downloading google map api"
                            mega-get ${GOOGLE_MAP_API_URL}

                            echo "unzipping google map api"
                            rm -fr ${GOOGLE_MAP_API_UNZIPPED}
                            unzip ${GOOGLE_MAP_API_FILE} -d ${GOOGLE_MAP_API_UNZIPPED}
                        fi

                        ls -lh

                        cd ${WORKSPACE}
                        pwd

                        echo "Applying Google Map API patches"
                        rm -fr app/src/debug/res/values/google_maps_api.xml
                        rm -fr app/src/release/res/values/google_maps_api.xml
                        cp -fr ${BUILD_LIB_DOWNLOAD_FOLDER}/${GOOGLE_MAP_API_UNZIPPED}/* app/src/

                    """
                }
            }
        }
        stage('Build SDK') {
            steps {
                script {
                    BUILD_STEP = 'Build SDK'
                }
                gitlabCommitStatus(name: 'Build SDK') {
                    script {
                        if (REBUILD_SDK != null && REBUILD_SDK.toLowerCase() == "yes") {
                            sh """
                                cd ${WORKSPACE}/app/src/main/jni
                                echo CLEANING SDK
                                bash build.sh clean
                            """
                        }
                    }

                    sh """
                    cd ${WORKSPACE}/app/src/main/jni
                    echo "=== START SDK BUILD===="
                    bash build.sh all
                    """
                }
            }
        }
        stage('Build APK(GMS)') {
            steps {
                script {
                    BUILD_STEP = 'Build APK (GMS)'
                }
                gitlabCommitStatus(name: 'Build APK (GMS)') {
                    sh './gradlew clean app:assembleGmsRelease'
                }
            }
        }
        stage('Sign APK(GMS)') {
            steps {
                script {
                    BUILD_STEP = 'Sign APK(GMS)'
                }
                gitlabCommitStatus(name: 'Sign APK(GMS)') {
                    withCredentials([
                            string(credentialsId: 'ANDROID_QA_SIGN_PASSWORD', variable: 'ANDROID_QA_SIGN_PASSWORD'),
                            file(credentialsId: 'ANDROID_QA_KEYSTORE', variable: 'ANDROID_QA_KEYSTORE')
                    ]) {
                        script {
                            sh '''
                                cd app/build/outputs/apk/gms/release
                                zipalign -v -p 4 app-*-unsigned.apk app-gms-release-unsigned-aligned.apk
                                apksigner sign --ks "$ANDROID_QA_KEYSTORE" --ks-pass "pass:$ANDROID_QA_SIGN_PASSWORD" --out app-gms-release-signed.apk app-gms-release-unsigned-aligned.apk
                                ls -lh
                                rm -fv *unsigned*.apk
                                pwd
                                ls -lh
                                cd -
                            '''
                        }
                    }
                }
            }
        }
        stage('Upload APK(GMS) to Firebase') {
            //            environment {
            //SIGNING_KEYSTORE = credentials('my-app-signing-keystore')
            //SIGNING_KEY_PASSWORD = credentials('my-app-signing-password')
            //            }
            steps {
                script {
                    BUILD_STEP = 'Upload APK(GMS) to Firebase'
                }
                withCredentials([
                        file(credentialsId: 'android_firebase_credentials', variable: 'FIREBASE_CONFIG')
                ]) {
                    script {
                        withEnv(["GOOGLE_APPLICATION_CREDENTIALS=$FIREBASE_CONFIG"]) {
                            sh './gradlew appDistributionUploadGmsRelease'
                        }
                    }
                }

                // Archive the APKs so that they can be downloaded from Jenkins
                // archiveArtifacts '**/*.apk'

                // Upload the APK to Google Play
                //androidApkUpload googleCredentialsId: 'Google Play', apkFilesPattern: '**/*-release.apk', trackName: 'beta'
            }
        }
        stage('Build APK(HMS)') {
            steps {
                script {
                    BUILD_STEP = 'Build APK(HMS)'
                }
                gitlabCommitStatus(name: 'Build APK(HMS)') {
                    sh './gradlew clean app:assembleHmsRelease'
                }
            }
        }
        stage('Sign APK(HMS)') {
            steps {
                script {
                    BUILD_STEP = 'Sign APK(HMS)'
                }
                gitlabCommitStatus(name: 'Sign APK(HMS)') {
                    withCredentials([
                            string(credentialsId: 'ANDROID_QA_SIGN_PASSWORD', variable: 'ANDROID_QA_SIGN_PASSWORD'),
                            file(credentialsId: 'ANDROID_QA_KEYSTORE', variable: 'ANDROID_QA_KEYSTORE')
                    ]) {
                        script {
                            sh '''
                                cd app/build/outputs/apk/hms/release
                                zipalign -v -p 4 app-*-unsigned.apk app-hms-release-unsigned-aligned.apk
                                apksigner sign --ks "$ANDROID_QA_KEYSTORE" --ks-pass "pass:$ANDROID_QA_SIGN_PASSWORD" --out app-hms-release-signed.apk app-hms-release-unsigned-aligned.apk
                                ls -lh
                                rm -fv *unsigned*.apk
                                pwd
                                ls -lh
                                cd -
                            '''
                        }
                    }
                }
            }
        }
        stage('Upload HMS APK to Firebase') {
            //            environment {
            //SIGNING_KEYSTORE = credentials('my-app-signing-keystore')
            //SIGNING_KEY_PASSWORD = credentials('my-app-signing-password')
            //            }
            steps {
                script {
                    BUILD_STEP = 'Upload HMS APK to Firebase'
                }
                withCredentials([
                        file(credentialsId: 'android_firebase_credentials', variable: 'FIREBASE_CONFIG')
                ]) {
                    script {
                        withEnv(["GOOGLE_APPLICATION_CREDENTIALS=$FIREBASE_CONFIG"]) {
                            sh './gradlew appDistributionUploadHmsRelease'
                        }
                    }
                }

                // Archive the APKs so that they can be downloaded from Jenkins
                // archiveArtifacts '**/*.apk'

                // Upload the APK to Google Play
                //androidApkUpload googleCredentialsId: 'Google Play', apkFilesPattern: '**/*-release.apk', trackName: 'beta'
            }
        }

//        stage('Deploy') {
//            environment {
//                // Assuming a file credential has been added to Jenkins, with the ID 'my-app-signing-keystore',
//                // this will export an environment variable during the build, pointing to the absolute path of
//                // the stored Android keystore file.  When the build ends, the temporarily file will be removed.
//                SIGNING_KEYSTORE = credentials('my-app-signing-keystore')
//
//                // Similarly, the value of this variable will be a password stored by the Credentials Plugin
//                SIGNING_KEY_PASSWORD = credentials('my-app-signing-password')
//            }
//            steps {
//                script {
//                    BUILD_STEP = 'Deploy'
//                }
//                // Build the app in release mode, and sign the APK using the environment variables
//                sh './gradlew assembleRelease'
//
//                // Archive the APKs so that they can be downloaded from Jenkins
//                archiveArtifacts '**/*.apk'
//
//                // Upload the APK to Google Play
//                androidApkUpload googleCredentialsId: 'Google Play', apkFilesPattern: '**/*-release.apk', trackName: 'beta'
//            }
//        // post {
//        //   success {
//        //     // Notify if the upload succeeded
//        //     mail to: 'beta-testers@example.com', subject: 'New build available!', body: 'Check it out!'
//        //   }
//        // }
//        }
        stage('Clean up') {
            steps {
                script {
                    BUILD_STEP = 'Clean Up'
                }
                gitlabCommitStatus(name: 'Clean Up') {
                    sh """
                    cd ${WORKSPACE}
                    echo "workspace size before clean: "
                    du -sh

                    cd ${WORKSPACE}/app/src/main/jni
                    bash build.sh clean
                    cd ${WORKSPACE}
                    ./gradlew clean
                    
                    echo "workspace size after clean: "
                    du -sh
                    """
                }
            }
        }
    }
// post {
//   failure {
//     // Notify developer team of the failure
//     mail to: 'android-devs@example.com', subject: 'Oops!', body: "Build ${env.BUILD_NUMBER} failed; ${env.BUILD_URL}"
//   }
// }
}

private String failureMessage(String lineBreak) {
    String message = ":x: Android Firebase Upload Build Failed!" +
            "${lineBreak}Target Branch:\t${gitlabTargetBranch}" +
            "${lineBreak}Source Branch:\t${gitlabSourceBranch}" +
            "${lineBreak}Author:\t${gitlabUserName}" +
            "${lineBreak}Commit:\t${GIT_COMMIT}"
    if (env.gitlabActionType == "PUSH") {
        message += "${lineBreak}Trigger Reason: git PUSH"
    } else if (env.gitlabActionType == "NOTE") {
        message += "${lineBreak}Trigger Reason: MR comment (${gitlabTriggerPhrase})"
    }
    return message
}

/**
 * get the value from GitLab MR description by key
 * @param key the key to check and read
 * @return actual value of key if key is specified. null otherwise.
 */
String getValueInMRDescriptionBy(String key) {
    if (key == null || key.isEmpty()) return null
    def description = env.gitlabMergeRequestDescription
    if (description == null) return null
    String[] lines = description.split('\n')
    for (String line : lines) {
        line = line.trim()
        if (line.startsWith(key)) {
            String value = line.substring(key.length() + 1)
            print("getValueInMRDescriptionBy(): " + key + " ==> " + value)
            return value
        }
    }
    return null
}

/**
 * check if a certain value is defined by checking the tag value
 * @param value value of tag
 * @return true if tag has a value. false if tag is null or zero length
 */
static boolean isDefined(String value) {
    return value != null && !value.isEmpty()
}

/**
 * checkout SDK by commit ID
 * @param sdkCommitId the commit ID to checkout
 */
private void checkoutSdkByCommit(String sdkCommitId) {
    sh """
    echo checkoutSdkByCommit
    cd $WORKSPACE
    cd app/src/main/jni/mega/sdk
    git checkout $sdkCommitId
    cd $WORKSPACE
    """
}

/**
 * checkout MEGAchat SDK by commit ID
 * @param megaChatCommitId the commit ID to checkout
 */
private void checkoutMegaChatSdkByCommit(String megaChatCommitId) {
    sh """
    echo checkoutMegaChatSdkByCommit
    cd $WORKSPACE
    cd app/src/main/jni/megachat/sdk
    git checkout $megaChatCommitId
    cd $WORKSPACE
    """
}

/**
 * checkout SDK by git tag
 * @param sdkTag the tag to checkout
 */
private void checkoutSdkByTag(String sdkTag) {
    sh """
    echo checkoutSdkByTag
    cd $WORKSPACE
    cd app/src/main/jni/mega/sdk
    git checkout tags/$sdkTag
    cd $WORKSPACE
    """
}

/**
 * checkout MEGAchat SDK by git tag
 * @param megaChatTag the tag to checkout
 */
private void checkoutMegaChatSdkByTag(String megaChatTag) {
    sh """
    echo checkoutMegaChatSdkByTag
    cd $WORKSPACE
    cd app/src/main/jni/megachat/sdk
    git checkout tags/$megaChatTag
    cd $WORKSPACE
    """
}

/**
 * checkout SDK by branch
 * @param sdkBranch the branch to checkout
 */
private void checkoutSdkByBranch(String sdkBranch) {
    sh "echo checkoutSdkByBranch"
    sh "cd \"$WORKSPACE\""
    sh 'git config --file=.gitmodules submodule.\"app/src/main/jni/mega/sdk\".url https://code.developers.mega.co.nz/sdk/sdk.git'
    sh "git config --file=.gitmodules submodule.\"app/src/main/jni/mega/sdk\".branch \"$sdkBranch\""
    sh 'git submodule sync'
    sh 'git submodule update --init --recursive --remote'
}

/**
 * checkout MEGAchat SDK by branch
 * @param megaChatBranch the branch to checkout
 */
private void checkoutMegaChatSdkByBranch(String megaChatBranch) {
    sh "echo checkoutMegaChatSdkByBranch"
    sh "cd \"$WORKSPACE\""
    sh 'git config --file=.gitmodules submodule.\"app/src/main/jni/megachat/sdk\".url https://code.developers.mega.co.nz/megachat/MEGAchat.git'
    sh "git config --file=.gitmodules submodule.\"app/src/main/jni/megachat/sdk\".branch \"${megaChatBranch}\""
    sh 'git submodule sync'
    sh 'git submodule update --init --recursive --remote'
}

/**
 * Check if this build is triggered by a GitLab Merge Request.
 * @return true if this build is triggerd by a GitLab MR. False if this build is triggerd
 * by a plain git push.
 */
private boolean hasGitLabMergeRequest() {
    return env.gitlabMergeRequestIid != null && !env.gitlabMergeRequestIid.isEmpty()
}

/**
 * send message to GitLab MR comment
 * @param message message to send
 */
private void sendMRComment(String message) {
    if (hasGitLabMergeRequest()) {
        def mrNumber = env.gitlabMergeRequestIid
        withCredentials([usernamePassword(credentialsId: 'Gitlab-Access-Token', usernameVariable: 'USERNAME', passwordVariable: 'TOKEN')]) {
            env.MARKDOWN_LINK = message
            env.MERGE_REQUEST_URL = "https://code.developers.mega.co.nz/api/v4/projects/199/merge_requests/${mrNumber}/notes"
            sh 'curl --request POST --header PRIVATE-TOKEN:$TOKEN --form body=\"${MARKDOWN_LINK}\" ${MERGE_REQUEST_URL}'
        }
    }
}

/**
 * compose the success message, which might be used for Slack or GitLab MR.
 * @param lineBreak Slack and MR comment use different line breaks. Slack uses "/n"
 * while GitLab MR uses "<br/>".
 * @return The success message to be sent
 */
private String successMessage(String lineBreak) {
    return ":rocket: Android APK Build uploaded successfully to Firebase AppDistribution!" +
            "${lineBreak}Version:\t${readAppVersion()}${APK_VERSION_NAME_FOR_CD}" +
            "${lineBreak}Last Commit Msg:\t${lastCommitMessage()}" +
            "${lineBreak}Target Branch:\t${gitlabTargetBranch}" +
            "${lineBreak}Source Branch:\t${gitlabSourceBranch}" +
            "${lineBreak}Author:\t${gitlabUserName}" +
            "${lineBreak}Commit:\t${GIT_COMMIT}" +
            "${lineBreak}Trigger Reason: ${getTriggerReason()}"
}

/**
 * get trigger reason
 * @return description for the trigger reason
 */
private String getTriggerReason() {
    if (env.gitlabActionType == "PUSH") {
        return "git PUSH into develop branch"
    } else if (env.gitlabActionType == "NOTE") {
        return "Manually by comment in GitLab MR(${gitlabTriggerPhrase})"
    } else {
        return "Other reasons${env.gitlabActionType}"
    }
}

/**
 * Read tester list from MR comment, so testers can be notified if APK is uploaded successfully.
 */
private String getTesterList() {
    if (env.gitlabActionType == "NOTE") {
        String triggerPhrase = env.gitlabTriggerPhrase
        if (triggerPhrase.startsWith("deliver_qa")) {
            String testerList = triggerPhrase.substring("deliver_qa".length()).trim()
            sh "echo testerList = $testerList"
            return testerList
        }
    }
    return ""
}

/**
 * Read SDK versions from MR description and assign the values into environment.
 */
private void checkSDKVersion() {
    SDK_COMMIT = getValueInMRDescriptionBy("SDK_COMMIT")
    MEGACHAT_COMMIT = getValueInMRDescriptionBy("MEGACHAT_COMMIT")

    SDK_TAG = getValueInMRDescriptionBy("SDK_TAG")
    MEGACHAT_TAG = getValueInMRDescriptionBy("MEGACHAT_TAG")

    SDK_BRANCH = getValueInMRDescriptionBy("SDK_BRANCH")
    if (!isDefined(SDK_BRANCH)) {
        SDK_BRANCH = "develop"
    }

    MEGACHAT_BRANCH = getValueInMRDescriptionBy("MEGACHAT_BRANCH")
    if (!isDefined(MEGACHAT_BRANCH)) {
        MEGACHAT_BRANCH = "develop"
    }
}

/**
 * read version name and version code from build.gradle
 * @return version name plus version code. Example: "6.6(433)"
 */
private String readAppVersion() {
    String versionCode = sh(script: "grep versionCode build.gradle | awk -F= '{print \$2}'", returnStdout: true).trim()
    String versionName = sh(script: "grep appVersion build.gradle | awk -F= '{print \$2}'", returnStdout: true).trim().replaceAll("\"", "")
    return versionName + "(" + versionCode + ")"
}

/**
 * read the last git commit message
 * @return last git commit message
 */
private String lastCommitMessage() {
    return sh(script: "git log --pretty=format:\"%x09%s\" -1", returnStdout: true).trim()
}


