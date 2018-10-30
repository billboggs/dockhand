package com.boxboat.jenkins.pipeline

import com.boxboat.jenkins.library.ServerConfig
import com.boxboat.jenkins.library.Utils
import com.boxboat.jenkins.library.docker.Image


class BoxDeploy extends BoxBase {

    String deployment = ""
    String release = ""
    public List<String> images
    public Map<String, String> events
    public Map<String, Map<String, String>> eventOverrides

    private static final String imageTagsFile = "image-tags.yml"

    BoxDeploy(Map config) {
        super(config)
        config?.each { k, v -> this[k] = v }
    }

    static def createBoxDeploy(Map config){
        def deploy = new BoxDeploy(config)
        deploy.steps.properties([
            [$class: 'BuildDiscarderProperty', strategy: [$class: 'LogRotator', numToKeepStr: '100']],
        ])
        return deploy
    }

    def init() {
        super.init()
        if (!deployment) {
            steps.error "'deployment' must be set"
        }
        if (!images || images.size() == 0) {
            steps.error "'images' must be set"
        }
        if (!events || events.size() == 0) {
            steps.error "'events' must be set"
        }
        if (!events.containsKey(deployment)) {
            steps.error "deployment '${deployment}' is not defined in 'events'"
        }
    }

    def deploy() {
        List<Image> images = images.collect { String v -> Image.fromImageString(v) }
        def primaryEvent = events.get(deployment)
        gitAccount.checkoutRepository(ServerConfig.buildVersionsGitRemoteUrl, "build-versions", 1)
        images.each { image ->
            def event = primaryEvent
            def eventOverridesDeployment = eventOverrides?.get(deployment)
            if (eventOverridesDeployment) {
                def eventOverridesImage = eventOverridesDeployment.get(image.path)
                if (eventOverridesImage) {
                    event = eventOverridesImage
                }
            }
            def filePath = "build-versions/${event}/${Utils.alphaNumericDashLower(image.path)}.yaml"
            def rc = steps.sh(returnStatus: true, script: """
                if [ -f "${filePath}" ]; then
                    cat "$filePath" >> "${imageTagsFile}"
                    exit 0
                fi
                exit 1
            """)
            if (rc != 0) {
                steps.error """
                    build-versions does not contain a version for event: ${event}, image '${image.path}'
                """
            }
        }

        // this is where you would do helm deploy

        this.steps.withKubeConfig(credentialsId: deployment + "-kube-config") {
            this.steps.sh """
                helm upgrade \
                ${release}-${deployment} \
                --namespace ${release}-${deployment} \
                --install \
                --values ./values-${deployment}.yaml,./${imageTagsFile} \
                .
            """
        }
    }

}
