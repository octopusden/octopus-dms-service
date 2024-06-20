import React, {Component} from 'react'
import {connect} from "react-redux";
import {componentsOperations} from "../duck";
import artifactList from "./presenter.jsx"
import get from "lodash/get"
import queryString from "query-string";
import history from "../../utils/history";
import {isPrintableArtifact} from "../common";

const mapStateToProps = (state) => {

  let adminMode = get(state, "components.adminMode", false)
  let showConfirmation = !!get(state, "confirmation")

  const currentArtifacts = get(state, "components.currentArtifacts")
  const {
    selectedComponent, selectedMinor, selectedVersion, selectedDocument, loadingArtifactsList, artifactsList
  } = currentArtifacts
  return {
    adminMode,
    showConfirmation,
    selectedComponent,
    selectedMinor,
    selectedVersion,
    selectedDocument,
    loadingArtifactsList,
    artifactsList
  }
}

const mapDispatchToProps = (dispatch) => {
  const fetchArtifactList = (componentId, minorVersion, version) => {
    dispatch(componentsOperations.getArtifactsList(componentId, minorVersion, version))
  }
  const fetchDocumentArtifact = (componentId, version, id, isPrintable, displayName) => {
    console.debug('fetchDocumentArtifact', componentId, version, id, isPrintable, displayName)
    isPrintable
        ? dispatch(componentsOperations.getDocumentArtifact(componentId, version, id, displayName))
        : dispatch(componentsOperations.getEmptyDocumentArtifact(componentId, version, id))
  }
  const deleteArtifact = (componentId, minorVersion, version, id) => {
    dispatch(componentsOperations.deleteArtifact(componentId, minorVersion, version, id))
  }
  return {
    fetchArtifactList,
    fetchDocumentArtifact,
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
  console.debug('currentUrlProps', currentUrlProps, 'selectedDocument', props.selectedDocument)
  return {
    ...currentUrlProps,
    id: id
  }
}

class ArtifactsList extends Component {

  componentDidUpdate(prevProps, prevState, snapshot) {
    const urlState = propsToUrl(this.props)
    console.debug('urlState', urlState)
    history.push({search: queryString.stringify(urlState)})

    const {
      fetchArtifactList, fetchDocumentArtifact,
      selectedComponent, selectedMinor, selectedVersion, selectedDocument, artifactsList
    } = this.props

    let {selectedComponent: prevSelectedComponent,
      selectedVersion: prevSelectedVersion,
      artifactsList: prevArtifactsList
    } = prevProps

    if (selectedComponent !== prevSelectedComponent || selectedVersion !== prevSelectedVersion) {
      fetchArtifactList(selectedComponent, selectedMinor, selectedVersion)
    }

    if (artifactsList.length > 0
        && (artifactsList.length !== prevArtifactsList.length || artifactsList.toString() !== prevArtifactsList.toString())
        && selectedDocument.id
    ) {
      let artifact = artifactsList.find(artifact => {
        return artifact.id === selectedDocument.id
      })
      let isPrintable = isPrintableArtifact(artifact)
      console.trace('selectedDocument:', selectedDocument, ', artifacts:', artifactsList, ', artifact:', artifact)
      fetchDocumentArtifact(selectedComponent, selectedVersion, artifact.id, isPrintable, artifact.displayName)
    }
  }

  componentDidMount() {
    const urlProps = queryString.parse(history.location.search)
    console.trace('urlProps', urlProps)
    const {component, minor, version, id} = urlProps
    const {fetchArtifactList, fetchDocumentArtifact} = this.props

    if (component && minor && version) {
      fetchArtifactList(component, minor, version)
      if (id) {
        fetchDocumentArtifact(component, version, +id, false, '')
      }
    }
  }

  render() {
    return artifactList(this.props)
  }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(ArtifactsList)
