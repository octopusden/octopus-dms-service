import {Component} from 'react'
import get from "lodash/get";
import {connect} from "react-redux";
import components from "./presenter.jsx";

const mapStateToProps = (state) => {
    const {
        components, hideError, errorMessage,
        confirmation, hideConfirmation, showConfirmation, currentArtifacts
    } = get(state, "components")
    const selectedComponent = get(currentArtifacts, "selectedComponent")
    const selectedVersion = get(currentArtifacts, "selectedVersion")
    return {
        components,
        hideError,
        errorMessage,
        confirmation,
        hideConfirmation,
        showConfirmation,
        currentArtifacts,
        selectedComponent,
        selectedVersion
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

class ComponentsComponent extends Component {
    render() {
        return components(this.props)
    }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(ComponentsComponent)
