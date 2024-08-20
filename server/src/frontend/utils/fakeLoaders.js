const getComponents = () => {
    return {
        "some-component": {
            name: "Some component",
        },
        "some-component2": {
            name: "Some component 2"
        }
    }
}

const getArtifacts = (componentId, version) => {
    return {
        "Artifact type1": [
            {name: componentId + "some-artifact-id 1", classifyer: "cl1"},
            {name: componentId + "some-artifact-id 2", classifyer: null}
        ],
        "Artifact type2": [
            {name: version + "some-artifact-id", classifyer: "cl1"},
            {name: version + "some-artifact-id2", classifyer: "cl2"},
            {name: version + "some-artifact-id3", classifyer: null}
        ]
    }
}

const getComponentVersions = (componentId) => {
    return ["-1", "-2", "-3"].map((cur) => (componentId + cur))
}

const getDocument = (componentId, version, artifactType, artifactName, artifactClassifyer) => {
    return "This is some text of artifact:\n" + componentId + "\n" + version +
        "\n" + artifactType + "\n" + artifactName + "\n" + artifactClassifyer
}

export default {
    getComponents,
    getComponentVersions,
    getArtifacts,
    getDocument
}
