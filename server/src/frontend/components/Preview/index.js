import React, { Component } from 'react'
import './style.css'
import preview from "./presenter.jsx";
import get from "lodash/get";
import {connect} from "react-redux";

const mapStateToProps = (state) => {
  const {selectedDocument, loadingDocumentArtifact } = get(state, "components.currentArtifacts")
  return {
    selectedDocument,
    loadingDocumentArtifact
  }
}

const mapDispatchToProps = (dispatch) => {
  return {}
}

const mergeProps = (stateProps, dispatchProps, ownProps) => {
  return {
    ...stateProps,
    ...dispatchProps,
    ...ownProps
  }
}

class Preview extends Component {
  render () {
    return preview(this.props)
  }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps) (Preview)