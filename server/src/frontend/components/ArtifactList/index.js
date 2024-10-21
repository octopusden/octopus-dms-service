import {Component} from 'react'
import {connect} from "react-redux";
import {componentsOperations} from "../duck";
import artifactList from "./presenter.jsx"
import get from "lodash/get"
import queryString from "query-string";
import history from "../../utils/history";
import {isPrintableArtifact} from "../common";

const mapStateToProps = (state) => {

    const adminMode = get(state, "components.adminMode", false)
    const showConfirmation = !!get(state, "confirmation")

    const currentArtifacts = get(state, "components.currentArtifacts")
    const {selectedComponent, selectedVersion, selectedDocument, artifacts} = currentArtifacts
    const {loadingArtifacts} = state.components
    return {
        adminMode,
        showConfirmation,
        selectedComponent,
        selectedVersion,
        selectedDocument,
        loadingArtifacts,
        artifacts
    }
}

const mapDispatchToProps = (dispatch) => {
    const getArtifactList = (componentId, version) => {
        dispatch(componentsOperations.getArtifacts(componentId, version))
    }
    const getDocument = (componentId, version, id, isPrintable, displayName) => {
        isPrintable
            ? dispatch(componentsOperations.getDocument(componentId, version, id, displayName))
            : dispatch(componentsOperations.getEmptyDocumentArtifact(componentId, version, id))
    }
    const deleteArtifact = (componentId, version, id) => {
        dispatch(componentsOperations.deleteArtifact(componentId, version, id))
    }
    return {
        getArtifactList,
        getDocument,
        deleteArtifact
    }
}

const mergeProps = (stateProps, dispatchProps, ownProps) => {
    return {
        ...stateProps,
        ...dispatchProps,
        ...ownProps
    }
}

const propsToUrl = (props) => {
    const currentUrlProps = queryString.parse(history.location.search)
    const {id} = props.selectedDocument
    return {
        ...currentUrlProps,
        artifactId: id
    }
}

class ArtifactsList extends Component {

    constructor(props, context) {
        super(props, context);
        const urlProps = queryString.parse(history.location.search)
        const {getDocument, selectedComponent, selectedVersion} = props
        const {artifactId} = urlProps
        if (artifactId) {
            getDocument(selectedComponent, selectedVersion, +artifactId, false, '')
        }
    }

    componentDidUpdate(prevProps, prevState, snapshot) {
        const urlState = propsToUrl(this.props)
        history.push({search: queryString.stringify(urlState)})

        const {
            getArtifactList, getDocument, selectedComponent, selectedVersion, selectedDocument, artifacts
        } = this.props

        const {
            selectedComponent: prevSelectedComponent,
            selectedVersion: prevSelectedVersion,
            artifacts: prevArtifacts
        } = prevProps

        if (selectedComponent !== prevSelectedComponent || selectedVersion !== prevSelectedVersion) {
            getArtifactList(selectedComponent, selectedVersion)
        }

        if (artifacts.length > 0
            && (artifacts.length !== prevArtifacts.length || artifacts.toString() !== prevArtifacts.toString())
            && selectedDocument.id
        ) {
            const artifact = artifacts.find(artifact => {
                return artifact.id === selectedDocument.id
            })
            const isPrintable = isPrintableArtifact(artifact)
            getDocument(selectedComponent, selectedVersion, artifact.id, isPrintable, artifact.displayName)
        }
    }

    componentDidMount() {
        const {
            selectedComponent,
            selectedVersion,
            selectedDocument,
            getArtifactList,
            getDocument
        } = this.props
        if (selectedComponent && selectedVersion) {
            getArtifactList(selectedComponent, selectedVersion)
            if (selectedDocument.id) {
                getDocument(selectedComponent, selectedVersion, +selectedDocument.id, false, '')
            }
        }
    }

    render() {
        return artifactList(this.props)
    }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(ArtifactsList)
