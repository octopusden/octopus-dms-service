import {connect} from "react-redux";
import {componentsOperations} from "../duck";
import {Component} from "react";
import componentGroupPane from "./presenter.jsx"

const mapStateToProps = (state) => {
    const {
        selectedComponentGroupTab
    } = state.components

    return {
        selectedComponentGroupTab
    }
}

const mapDispatchToProps = (dispatch) => {
    const handleComponentGroupTabChange = (selectedComponentGroupTab) => {
        dispatch(componentsOperations.handleComponentGroupTabChange(selectedComponentGroupTab))
    }
    return {
        handleComponentGroupTabChange
    }
}

function mergeProps(stateProps, dispatchProps, ownProps) {
    return {
        ...stateProps,
        ...dispatchProps,
        ...ownProps
    }
}

class ComponentGroupPane extends Component {
    render() {
        return componentGroupPane(this.props)
    }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(ComponentGroupPane)