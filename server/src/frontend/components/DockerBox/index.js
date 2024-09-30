import React from "react";
import {Icon} from "@blueprintjs/core";
import copy from "copy-to-clipboard";
import "./style.css";

/**
 * DockerBox component
 * to display docker pull command and copy it to clipboard
 *
 * @param {string} image - Docker image name
 */
class DockerBox extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            isIconChanged: false,
            text: 'docker pull ' + props.image,
        };
    }

    handleIconClick = () => {
        this.setState({ isIconChanged: true });
        copy(this.state.text)
        setTimeout(() => {
            this.setState({ isIconChanged: false });
        }, 1000);
    };

    render() {
        const { image } = this.props
        const { isIconChanged, text } = this.state;
        return (
            <div className="docker-box">
                <code className="code-block">{ text }</code>
                <Icon icon={isIconChanged ? "confirm" : "clipboard"}
                      className="copy-button"
                      onClick={this.handleIconClick}/></div>
        );
    }
}

export default DockerBox;