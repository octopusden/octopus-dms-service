import {Component} from 'react'
import meta from "./presenter.jsx";
import get from "lodash/get";
import {connect} from "react-redux";


const mapStateToProps = (state) => {
    const { selectedComponent, selectedComponentName, selectedVersion} = get(state, "components.currentArtifacts")
    return {
        selectedComponent,
        selectedComponentName,
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

class Meta extends Component {
    render() {
        return meta(this.props)
    }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(Meta)